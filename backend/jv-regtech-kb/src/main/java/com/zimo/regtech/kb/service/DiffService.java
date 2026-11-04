package com.zimo.regtech.kb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.regtech.common.exception.BizException;
import com.zimo.regtech.common.result.Page;
import com.zimo.regtech.kb.client.EmbeddingClient;
import com.zimo.regtech.kb.entity.DiffRecord;
import com.zimo.regtech.kb.entity.LegalArticle;
import com.zimo.regtech.kb.mapper.KbMapper;
import com.zimo.regtech.kb.model.KbRequests;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 条文差异对比：
 * <ul>
 *   <li>单对单：按行做 LCS 差异，输出可直接渲染的 diff 块并落库留痕</li>
 *   <li>多地域 / 多场景：按场景向量召回各法域最相关条文，交叉打分并给出来源出处</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiffService {

    private static final int MAX_LINES = 2000;
    private static final int SNIPPET = 600;

    private final KbMapper kbMapper;
    private final ObjectMapper objectMapper;
    private final EmbeddingClient embeddingClient;
    private final RestClient restClient;

    @Value("${regtech.services.ai:http://127.0.0.1:28105/api/regtech/ai}")
    private String aiBase;

    @Transactional
    public Map<String, Object> compare(Long leftId, Long rightId) {
        if (leftId == null || rightId == null) {
            throw new BizException("请选择需要对比的两条法规");
        }
        if (leftId.equals(rightId)) {
            throw new BizException("请选择两条不同的法规进行对比");
        }
        LegalArticle left = kbMapper.selectArticle(leftId);
        LegalArticle right = kbMapper.selectArticle(rightId);
        if (left == null || right == null) {
            throw new BizException("对比的法规不存在");
        }

        List<String> leftLines = lines(left.getContent());
        List<String> rightLines = lines(right.getContent());

        List<Map<String, Object>> blocks = compute(leftLines, rightLines);
        long same = blocks.stream()
                .filter(b -> "equal".equals(b.get("type")))
                .count();
        double similarity = (leftLines.isEmpty() && rightLines.isEmpty())
                ? 1d
                : Math.round((2d * same / (leftLines.size() + rightLines.size())) * 10000d) / 10000d;

        String changeType = resolveChangeType(similarity, leftLines, rightLines);

        DiffRecord record = new DiffRecord();
        record.setLeftId(leftId);
        record.setRightId(rightId);
        record.setLeftTitle(label(left));
        record.setRightTitle(label(right));
        record.setChangeType(changeType);
        record.setSimilarity(similarity);
        try {
            record.setDiffJson(objectMapper.writeValueAsString(blocks));
        } catch (Exception e) {
            record.setDiffJson("[]");
        }
        kbMapper.insertDiff(record);

        Map<Long, Map<String, Object>> citations = citations(List.of(leftId, rightId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("diffId", record.getId());
        result.put("similarity", similarity);
        result.put("changeType", changeType);
        result.put("blocks", blocks);
        result.put("left", brief(left, citations));
        result.put("right", brief(right, citations));
        result.put("citationNote", "相似度为正文逐行 LCS 对齐后的重合比例；来源出处标注该条文所属知识库数据集与文档。");
        return result;
    }

    public Page<DiffRecord> page(long current, long size) {
        long limit = Math.max(1, Math.min(size, 100));
        long offset = Math.max(0, (Math.max(1, current) - 1) * limit);
        long total = kbMapper.countDiffs();
        List<DiffRecord> records = total == 0 ? List.of() : kbMapper.selectDiffs(offset, limit);
        // 列表不需要完整 diff 报文
        records.forEach(item -> item.setDiffJson(null));
        return Page.of(records, total, current, limit);
    }

    public DiffRecord get(Long id) {
        DiffRecord record = kbMapper.selectDiff(id);
        if (record == null) {
            throw new BizException("对比记录不存在");
        }
        return record;
    }

    public void delete(Long id) {
        kbMapper.deleteDiff(id);
    }

    // ---------------------------------------------------------------- 多地域 / 多场景对比

    /** 库中已有条文的法域及数量 */
    public List<Map<String, Object>> jurisdictions() {
        return kbMapper.countArticlesByJurisdiction();
    }

    /**
     * 场景 × 法域交叉对比：每个场景在每个法域召回最相关条文，
     * 并给出跨地域语义相似度与知识库出处。
     */
    public Map<String, Object> matrix(KbRequests.MatrixForm form) {
        List<String> scenarios = clean(form.getScenarios());
        if (scenarios.isEmpty()) {
            throw new BizException("请填写需要对比的法律场景，例如“投资者适当性”");
        }

        List<Map<String, Object>> jurisdictionStats = kbMapper.countArticlesByJurisdiction();
        List<String> jurisdictions = clean(form.getJurisdictions());
        if (jurisdictions.isEmpty()) {
            for (Map<String, Object> row : jurisdictionStats) {
                String code = str(row.get("jurisdiction"));
                if (StringUtils.hasText(code)) {
                    jurisdictions.add(code);
                }
            }
        }
        if (jurisdictions.size() < 2) {
            throw new BizException("至少选择两个法域才能做地域对比");
        }

        int topK = Math.max(1, Math.min(form.getTopK(), 5));
        List<List<List<LegalArticle>>> hitsAll = new ArrayList<>();
        List<Long> ids = new ArrayList<>();

        for (String scenario : scenarios) {
            List<Double> vector = embedQuietly(scenario);
            List<List<LegalArticle>> perJurisdiction = new ArrayList<>();
            for (String code : jurisdictions) {
                List<LegalArticle> hits = searchInJurisdiction(scenario, vector, code, topK);
                perJurisdiction.add(hits);
                for (LegalArticle item : hits) {
                    ids.add(item.getId());
                }
            }
            hitsAll.add(perJurisdiction);
        }

        Map<Long, Map<String, Object>> citationIndex = citations(ids);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < scenarios.size(); i++) {
            rows.add(scenarioRow(scenarios.get(i), jurisdictions, hitsAll.get(i), citationIndex));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenarios", rows);
        result.put("jurisdictions", jurisdictionStats);
        result.put("topK", topK);
        result.put("explain", form.isExplain() ? explain(rows, form.getModel()) : null);
        return result;
    }

    private Map<String, Object> scenarioRow(String scenario, List<String> jurisdictions,
                                            List<List<LegalArticle>> hits,
                                            Map<Long, Map<String, Object>> citationIndex) {
        List<Map<String, Object>> cells = new ArrayList<>();
        List<LegalArticle> primaries = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (int i = 0; i < jurisdictions.size(); i++) {
            String code = jurisdictions.get(i);
            List<LegalArticle> items = hits.get(i);
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("jurisdiction", code);
            cell.put("found", !items.isEmpty());
            if (items.isEmpty()) {
                missing.add(code);
                cell.put("article", null);
                cell.put("alternatives", List.of());
            } else {
                LegalArticle primary = items.get(0);
                primaries.add(primary);
                cell.put("score", primary.getScore());
                cell.put("article", brief(primary, citationIndex));
                List<Map<String, Object>> alternatives = new ArrayList<>();
                for (int k = 1; k < items.size(); k++) {
                    alternatives.add(brief(items.get(k), citationIndex));
                }
                cell.put("alternatives", alternatives);
            }
            cells.add(cell);
        }

        List<Map<String, Object>> pairs = new ArrayList<>();
        for (int i = 0; i < primaries.size(); i++) {
            for (int j = i + 1; j < primaries.size(); j++) {
                LegalArticle left = primaries.get(i);
                LegalArticle right = primaries.get(j);
                Double similarity = null;
                try {
                    similarity = kbMapper.similarityBetween(left.getId(), right.getId());
                } catch (Exception e) {
                    log.debug("计算条文相似度失败：{}", e.getMessage());
                }
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put("from", left.getJurisdiction());
                pair.put("to", right.getJurisdiction());
                pair.put("fromId", left.getId());
                pair.put("toId", right.getId());
                pair.put("fromLabel", label(left));
                pair.put("toLabel", label(right));
                pair.put("similarity", similarity);
                pair.put("level", similarity == null ? "无法计算"
                        : similarity >= 0.85d ? "高度一致"
                        : similarity >= 0.7d ? "较为接近"
                        : similarity >= 0.55d ? "部分重合" : "差异明显");
                pairs.add(pair);
            }
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", scenario);
        row.put("cells", cells);
        row.put("pairs", pairs);
        row.put("missing", missing);
        row.put("coverage", (jurisdictions.size() - missing.size()) + "/" + jurisdictions.size());
        return row;
    }

    private List<LegalArticle> searchInJurisdiction(String scenario, List<Double> vector,
                                                    String code, int topK) {
        List<LegalArticle> hits = new ArrayList<>();
        if (vector != null && !vector.isEmpty()) {
            try {
                hits.addAll(kbMapper.searchArticlesByVectorInJurisdiction(
                        EmbeddingClient.toVectorLiteral(vector), code, topK));
            } catch (Exception e) {
                log.warn("法域 {} 的向量检索失败，降级关键词：{}", code, e.getMessage());
            }
        }
        if (hits.isEmpty()) {
            for (LegalArticle item : kbMapper.searchArticlesByKeyword(scenario, code, topK)) {
                if (item.getScore() == null) {
                    item.setScore(0.5d);
                }
                hits.add(item);
            }
        }
        return hits;
    }

    private List<Double> embedQuietly(String text) {
        try {
            return embeddingClient.embed(text);
        } catch (Exception e) {
            log.warn("场景向量化失败，降级关键词检索：{}", e.getMessage());
            return List.of();
        }
    }

    // ---------------------------------------------------------------- 来源出处

    /** articleId → 出处信息（知识库数据集 / 文档 / 条文库直采） */
    private Map<Long, Map<String, Object>> citations(List<Long> ids) {
        Map<Long, Map<String, Object>> index = new LinkedHashMap<>();
        Set<Long> distinct = new LinkedHashSet<>(ids);
        if (distinct.isEmpty()) {
            return index;
        }
        for (Map<String, Object> row : kbMapper.selectArticleCitations(new ArrayList<>(distinct))) {
            Long articleId = asLong(row.get("article_id"));
            if (articleId == null || index.containsKey(articleId)) {
                continue;
            }
            index.put(articleId, citation(row));
        }
        return index;
    }

    private Map<String, Object> citation(Map<String, Object> row) {
        String lawName = str(row.get("law_name"));
        String articleNo = str(row.get("article_no"));
        String jurisdiction = str(row.get("jurisdiction"));
        String status = str(row.get("status"));
        String version = str(row.get("version"));
        String effectiveDate = str(row.get("effective_date"));

        StringBuilder cite = new StringBuilder("《").append(lawName).append("》").append(articleNo);
        List<String> meta = new ArrayList<>();
        if (StringUtils.hasText(jurisdiction)) {
            meta.add(jurisdiction);
        }
        if (StringUtils.hasText(status)) {
            meta.add(status);
        }
        if (StringUtils.hasText(version)) {
            meta.add(version);
        }
        if (StringUtils.hasText(effectiveDate)) {
            meta.add("生效 " + effectiveDate);
        }
        if (!meta.isEmpty()) {
            cite.append("（").append(String.join(" · ", meta)).append("）");
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("citation", cite.toString());
        map.put("articleId", asLong(row.get("article_id")));

        Object documentId = row.get("document_id");
        if (documentId != null) {
            String datasetName = str(row.get("dataset_name"));
            String documentTitle = str(row.get("document_title"));
            map.put("channel", "知识库文档");
            map.put("datasetId", asLong(row.get("dataset_id")));
            map.put("datasetCode", str(row.get("dataset_code")));
            map.put("datasetName", datasetName);
            map.put("documentId", asLong(documentId));
            map.put("documentTitle", documentTitle);
            map.put("sourceType", str(row.get("source_type")));
            map.put("documentCreateTime", row.get("document_create_time"));
            map.put("explain", "出处：知识库「"
                    + (StringUtils.hasText(datasetName) ? datasetName : "未归属数据集") + "」→ 文档《"
                    + documentTitle + "》。该文档入库时切分为分块并生成向量，同时把条文写入条文库 legal_article；"
                    + "本条由条文库向量索引命中，正文与文档原文一致。");
        } else {
            map.put("channel", "条文库直采");
            map.put("explain", "出处：《" + lawName + "》" + articleNo
                    + "。该条文在条文库 legal_article 中直接维护，暂未关联知识库文档，"
                    + "因此只有法规级出处、没有文档与分块级溯源；如需完整溯源可在「法律知识库」上传该法规原文。");
        }
        return map;
    }

    // ---------------------------------------------------------------- AI 解读

    private String explain(List<Map<String, Object>> rows, String model) {
        StringBuilder context = new StringBuilder();
        for (Map<String, Object> row : rows) {
            context.append("场景：").append(row.get("name")).append("\n");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cells = (List<Map<String, Object>>) row.get("cells");
            for (Map<String, Object> cell : cells) {
                @SuppressWarnings("unchecked")
                Map<String, Object> article = (Map<String, Object>) cell.get("article");
                if (article == null) {
                    context.append("- ").append(cell.get("jurisdiction")).append("：未检索到相关条文\n");
                    continue;
                }
                context.append("- [").append(cell.get("jurisdiction")).append("] 《")
                        .append(article.get("lawName")).append("》").append(article.get("articleNo"));
                if (article.get("title") != null) {
                    context.append("（").append(article.get("title")).append("）");
                }
                context.append("：").append(snippet(str(article.get("content")), 300)).append("\n");
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pairs = (List<Map<String, Object>>) row.get("pairs");
            for (Map<String, Object> pair : pairs) {
                context.append("- 相似度 ").append(pair.get("from")).append(" ↔ ").append(pair.get("to"))
                        .append("：").append(pair.get("similarity"))
                        .append("（").append(pair.get("level")).append("）\n");
            }
        }

        String system = "你是 NCS RegTech 的法规对比分析助手。只依据给定条文做对比，"
                + "用中文分场景说明各地域的共同要求与实质差异，并注明地域与条款号；条文未覆盖处不要臆测。";
        String user = "以下是按场景与法域召回的条文及其语义相似度，请给出对比解读（每个场景 3-5 条要点）：\n\n" + context;

        Map<String, Object> payload = new LinkedHashMap<>();
        if (StringUtils.hasText(model)) {
            payload.put("model", model);
        }
        payload.put("temperature", 0.2);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        ));
        try {
            Map<String, Object> body = restClient.post()
                    .uri(aiBase + "/internal/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Object data = body == null ? null : body.get("data");
            return data instanceof Map<?, ?> map && map.get("content") != null
                    ? String.valueOf(map.get("content")) : null;
        } catch (Exception e) {
            log.warn("AI 解读生成失败：{}", e.getMessage());
            return "AI 解读暂不可用（" + e.getMessage() + "），已返回结构化对比结果。";
        }
    }

    // ---------------------------------------------------------------- 工具

    private static List<String> clean(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                String trimmed = value.trim();
                if (!result.contains(trimmed)) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String snippet(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    // ---------------------------------------------------------------- 差异算法

    private List<Map<String, Object>> compute(List<String> left, List<String> right) {
        int n = left.size();
        int m = right.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                dp[i][j] = left.get(i).equals(right.get(j))
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }

        List<Map<String, Object>> blocks = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (left.get(i).equals(right.get(j))) {
                blocks.add(block("equal", i + 1, j + 1, left.get(i)));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                blocks.add(block("delete", i + 1, null, left.get(i)));
                i++;
            } else {
                blocks.add(block("insert", null, j + 1, right.get(j)));
                j++;
            }
        }
        while (i < n) {
            blocks.add(block("delete", i + 1, null, left.get(i)));
            i++;
        }
        while (j < m) {
            blocks.add(block("insert", null, j + 1, right.get(j)));
            j++;
        }
        return blocks;
    }

    private static Map<String, Object> block(String type, Integer leftLine, Integer rightLine, String text) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", type);
        block.put("leftLine", leftLine);
        block.put("rightLine", rightLine);
        block.put("text", text);
        return block;
    }

    private static String resolveChangeType(double similarity, List<String> left, List<String> right) {
        if (similarity >= 0.9999d) {
            return "无变化";
        }
        if (left.isEmpty()) {
            return "新增";
        }
        if (right.isEmpty()) {
            return "删除";
        }
        return similarity >= 0.6d ? "局部修订" : "重大修订";
    }

    private static List<String> lines(String content) {
        List<String> lines = new ArrayList<>();
        if (content == null) {
            return lines;
        }
        for (String line : content.split("\n")) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    private static String label(LegalArticle article) {
        return "《" + article.getLawName() + "》" + article.getArticleNo()
                + (article.getVersion() == null ? "" : " " + article.getVersion());
    }

    private static Map<String, Object> brief(LegalArticle article, Map<Long, Map<String, Object>> citations) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", article.getId());
        map.put("code", article.getCode());
        map.put("lawName", article.getLawName());
        map.put("articleNo", article.getArticleNo());
        map.put("title", article.getTitle());
        map.put("jurisdiction", article.getJurisdiction());
        map.put("version", article.getVersion());
        map.put("status", article.getStatus());
        map.put("effectiveDate", article.getEffectiveDate() == null
                ? null : article.getEffectiveDate().toString());
        map.put("tags", article.getTags());
        map.put("score", article.getScore());
        map.put("content", article.getContent());
        Map<String, Object> citation = citations.get(article.getId());
        if (citation != null) {
            map.put("citation", citation);
        }
        return map;
    }
}
