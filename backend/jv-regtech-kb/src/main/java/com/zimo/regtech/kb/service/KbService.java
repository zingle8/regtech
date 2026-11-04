package com.zimo.regtech.kb.service;

import com.zimo.regtech.common.context.UserContext;
import com.zimo.regtech.common.exception.BizException;
import com.zimo.regtech.common.result.Page;
import com.zimo.regtech.kb.client.EmbeddingClient;
import com.zimo.regtech.kb.entity.ArticleLog;
import com.zimo.regtech.kb.entity.Dataset;
import com.zimo.regtech.kb.entity.KbChunk;
import com.zimo.regtech.kb.entity.KbDocument;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 法律知识库：文档入库（切分 + 向量化）、语义检索、条文检索、RAG 问答。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbService {

    private static final int CHUNK_SIZE = 600;
    private static final int CHUNK_OVERLAP = 80;
    private static final Pattern ARTICLE_NO = Pattern.compile("第[一二三四五六七八九十百零〇0-9]+条");

    private final KbMapper kbMapper;
    private final EmbeddingClient embeddingClient;
    private final RestClient restClient;

    @Value("${regtech.services.ai:http://127.0.0.1:28105/api/regtech/ai}")
    private String aiBase;

    // ---------------------------------------------------------------- 数据集

    public List<Dataset> datasets(String keyword) {
        return kbMapper.selectDatasets(keyword);
    }

    @Transactional
    public Dataset createDataset(KbRequests.DatasetForm form) {
        Dataset dataset = new Dataset();
        dataset.setCode(StringUtils.hasText(form.getCode())
                ? form.getCode() : "ds-" + System.currentTimeMillis());
        dataset.setName(form.getName());
        dataset.setDescription(form.getDescription());
        dataset.setJurisdiction(form.getJurisdiction());
        dataset.setEmbeddingModel(StringUtils.hasText(form.getEmbeddingModel())
                ? form.getEmbeddingModel() : embeddingClient.model());
        kbMapper.insertDataset(dataset);
        return dataset;
    }

    public void updateDataset(Long id, KbRequests.DatasetForm form) {
        Dataset dataset = new Dataset();
        dataset.setId(id);
        dataset.setName(form.getName());
        dataset.setDescription(form.getDescription());
        dataset.setJurisdiction(form.getJurisdiction());
        dataset.setEmbeddingModel(form.getEmbeddingModel());
        kbMapper.updateDataset(dataset);
    }

    @Transactional
    public void deleteDataset(Long id) {
        List<KbDocument> documents = kbMapper.selectDocuments(id, null, 0, 500);
        for (KbDocument document : documents) {
            kbMapper.deleteChunksByDocument(document.getId());
            kbMapper.deleteDocument(document.getId());
        }
        kbMapper.deleteDataset(id);
    }

    // ---------------------------------------------------------------- 文档

    public Page<KbDocument> documents(Long datasetId, String keyword, long current, long size) {
        long limit = Math.max(1, Math.min(size, 100));
        long offset = Math.max(0, (Math.max(1, current) - 1) * limit);
        long total = kbMapper.countDocuments(datasetId, keyword);
        List<KbDocument> records = total == 0
                ? List.of()
                : kbMapper.selectDocuments(datasetId, keyword, offset, limit);
        return Page.of(records, total, current, limit);
    }

    public KbDocument document(Long id) {
        KbDocument document = kbMapper.selectDocument(id);
        if (document == null) {
            throw new BizException("文档不存在");
        }
        return document;
    }

    /**
     * 文档入库：切分 → 批量向量化 → 写入分块表。
     * 向量服务不可用时仍会入库（embedding 为空），检索自动降级为关键词。
     */
    @Transactional
    public Map<String, Object> createDocument(KbRequests.DocumentForm form) {
        KbDocument document = new KbDocument();
        document.setDatasetId(form.getDatasetId());
        document.setTitle(form.getTitle());
        document.setSourceType(form.getSourceType());
        document.setJurisdiction(form.getJurisdiction());
        document.setEffectiveDate(form.getEffectiveDate());
        document.setStatus(form.getStatus());
        document.setContent(form.getContent());
        document.setCharCount(form.getContent().length());
        kbMapper.insertDocument(document);

        List<String> pieces = split(form.getContent());
        List<List<Double>> vectors = safeEmbed(pieces);

        for (int i = 0; i < pieces.size(); i++) {
            KbChunk chunk = new KbChunk();
            chunk.setDatasetId(document.getDatasetId());
            chunk.setDocumentId(document.getId());
            chunk.setChunkNo(i + 1);
            chunk.setArticleNo(detectArticleNo(pieces.get(i)));
            chunk.setContent(pieces.get(i));
            chunk.setTokens(Math.max(1, pieces.get(i).length() / 2));
            List<Double> vector = vectors.size() > i ? vectors.get(i) : List.of();
            kbMapper.insertChunk(chunk, vector.isEmpty() ? null : EmbeddingClient.toVectorLiteral(vector));
        }

        kbMapper.updateDocumentChunkCount(document.getId(), pieces.size());
        if (document.getDatasetId() != null) {
            kbMapper.refreshDatasetStats(document.getDatasetId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", document.getId());
        result.put("chunks", pieces.size());
        result.put("embedded", !vectors.isEmpty());
        return result;
    }

    @Transactional
    public void deleteDocument(Long id) {
        KbDocument document = document(id);
        kbMapper.deleteChunksByDocument(id);
        kbMapper.deleteDocument(id);
        if (document.getDatasetId() != null) {
            kbMapper.refreshDatasetStats(document.getDatasetId());
        }
    }

    // ---------------------------------------------------------------- 检索

    public Map<String, Object> search(KbRequests.SearchForm form) {
        if (!StringUtils.hasText(form.getQuery())) {
            throw new BizException("请输入检索内容");
        }
        int topK = Math.max(1, Math.min(form.getTopK(), 30));
        String mode = "vector";
        List<KbChunk> chunks = List.of();

        if (!"keyword".equalsIgnoreCase(form.getMode())) {
            try {
                List<Double> vector = embeddingClient.embed(form.getQuery());
                if (!vector.isEmpty()) {
                    chunks = kbMapper.searchChunksByVector(
                            EmbeddingClient.toVectorLiteral(vector), form.getDatasetId(), topK);
                }
            } catch (Exception e) {
                log.warn("向量检索失败，降级为关键词检索：{}", e.getMessage());
                mode = "keyword-fallback";
            }
        }
        if (chunks.isEmpty()) {
            mode = "keyword";
            List<String> terms = trigrams(form.getQuery());
            chunks = terms.isEmpty()
                    ? kbMapper.searchChunksByKeyword(form.getQuery(), form.getDatasetId(), topK)
                    : kbMapper.searchChunksByTerms(terms, terms.size(), form.getDatasetId(), topK);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("query", form.getQuery());
        result.put("total", chunks.size());
        result.put("records", chunks);
        return result;
    }

    // ---------------------------------------------------------------- 法条

    public Page<LegalArticle> articles(String keyword, String jurisdiction, long current, long size) {
        long limit = Math.max(1, Math.min(size, 100));
        long offset = Math.max(0, (Math.max(1, current) - 1) * limit);
        long total = kbMapper.countArticles(keyword, jurisdiction);
        List<LegalArticle> records = total == 0
                ? List.of()
                : kbMapper.selectArticles(keyword, jurisdiction, offset, limit);
        return Page.of(records, total, current, limit);
    }

    public LegalArticle article(Long id) {
        LegalArticle article = kbMapper.selectArticle(id);
        if (article == null) {
            throw new BizException("条款不存在");
        }
        return article;
    }

    @Transactional
    public LegalArticle createArticle(KbRequests.ArticleForm form) {
        LegalArticle article = toArticle(form);
        article.setCode(StringUtils.hasText(form.getCode()) ? form.getCode() : "art-" + System.currentTimeMillis());
        kbMapper.insertArticle(article, embedOrNull(form.getLawName() + form.getArticleNo() + form.getContent()));
        writeLog(ArticleLog.CREATE, null, article, "新增条款");
        return article;
    }

    @Transactional
    public void updateArticle(Long id, KbRequests.ArticleForm form) {
        LegalArticle before = article(id);
        LegalArticle article = toArticle(form);
        article.setId(id);
        String embedding = embedOrNull(form.getLawName() + form.getArticleNo() + form.getContent());
        kbMapper.updateArticle(article, embedding);
        writeLog(ArticleLog.UPDATE, before, article, diffSummary(before, article));
    }

    @Transactional
    public void deleteArticle(Long id) {
        LegalArticle before = article(id);
        kbMapper.deleteArticle(id);
        writeLog(ArticleLog.DELETE, before, null, "删除条款");
    }

    /** 最近的条文变更（新增 / 更新 / 删除），供总览页动态列表使用 */
    public List<ArticleLog> articleChanges(int limit) {
        return kbMapper.selectArticleLogs(Math.max(1, Math.min(limit, 50)));
    }

    /**
     * 写变更日志：after 为本次提交的表单（新增 / 更新），before 为变更前快照（更新 / 删除）。
     * 日志失败不影响主流程。
     */
    private void writeLog(String action, LegalArticle before, LegalArticle after, String summary) {
        try {
            ArticleLog entry = new ArticleLog();
            entry.setAction(action);
            entry.setArticleId(after != null && after.getId() != null ? after.getId()
                    : (before == null ? null : before.getId()));
            entry.setCode(prefer(after == null ? null : after.getCode(),
                    before == null ? null : before.getCode()));
            entry.setLawName(prefer(after == null ? null : after.getLawName(),
                    before == null ? null : before.getLawName()));
            entry.setArticleNo(prefer(after == null ? null : after.getArticleNo(),
                    before == null ? null : before.getArticleNo()));
            entry.setTitle(prefer(after == null ? null : after.getTitle(),
                    before == null ? null : before.getTitle()));
            entry.setSummary(summary);
            String operator = UserContext.getUsername();
            entry.setOperatorName(StringUtils.hasText(operator) ? operator : "系统");
            kbMapper.insertArticleLog(entry);
        } catch (Exception e) {
            log.warn("写入条文变更日志失败：{}", e.getMessage());
        }
    }

    private String diffSummary(LegalArticle before, LegalArticle after) {
        List<String> changed = new ArrayList<>();
        if (changed(before.getLawName(), after.getLawName())) {
            changed.add("法规名称");
        }
        if (changed(before.getArticleNo(), after.getArticleNo())) {
            changed.add("条款号");
        }
        if (changed(before.getTitle(), after.getTitle())) {
            changed.add("标题");
        }
        if (changed(before.getStatus(), after.getStatus())) {
            changed.add("效力状态");
        }
        if (changed(before.getJurisdiction(), after.getJurisdiction())) {
            changed.add("法域");
        }
        if (changed(before.getTags(), after.getTags())) {
            changed.add("标签");
        }
        if (changed(before.getContent(), after.getContent())) {
            changed.add("正文");
        }
        return changed.isEmpty() ? "内容未变化" : "更新 " + String.join("、", changed);
    }

    private static boolean changed(String before, String after) {
        return after != null && !after.equals(before);
    }

    private static String prefer(String after, String before) {
        return StringUtils.hasText(after) ? after : before;
    }

    /**
     * 条文检索：向量召回 + 关键词召回，按分数合并去重。
     */
    public Map<String, Object> searchArticles(String query, String jurisdiction, int topK) {
        if (!StringUtils.hasText(query)) {
            throw new BizException("请输入检索内容");
        }
        int limit = Math.max(1, Math.min(topK, 30));
        Map<Long, LegalArticle> merged = new LinkedHashMap<>();
        String mode = "hybrid";

        try {
            List<Double> vector = embeddingClient.embed(query);
            if (!vector.isEmpty()) {
                for (LegalArticle item : kbMapper.searchArticlesByVector(
                        EmbeddingClient.toVectorLiteral(vector), limit)) {
                    merged.put(item.getId(), item);
                }
            }
        } catch (Exception e) {
            log.warn("条文向量检索失败，降级为关键词检索：{}", e.getMessage());
            mode = "keyword";
        }

        for (LegalArticle item : kbMapper.searchArticlesByKeyword(query, jurisdiction, limit)) {
            LegalArticle exists = merged.get(item.getId());
            if (exists == null) {
                merged.put(item.getId(), item);
            }
        }

        List<LegalArticle> records = new ArrayList<>(merged.values());
        records.sort((a, b) -> Double.compare(
                b.getScore() == null ? 0 : b.getScore(),
                a.getScore() == null ? 0 : a.getScore()));
        if (records.size() > limit) {
            records = records.subList(0, limit);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("query", query);
        result.put("total", records.size());
        result.put("records", records);
        return result;
    }

    // ---------------------------------------------------------------- RAG

    /** 检索增强问答：召回条文 → 拼上下文 → 调本地模型 */
    public Map<String, Object> rag(KbRequests.RagForm form) {
        long start = System.currentTimeMillis();
        KbRequests.SearchForm searchForm = new KbRequests.SearchForm();
        searchForm.setQuery(form.getQuestion());
        searchForm.setDatasetId(form.getDatasetId());
        searchForm.setTopK(Math.max(1, Math.min(form.getTopK(), 12)));

        Map<String, Object> searchResult = search(searchForm);
        @SuppressWarnings("unchecked")
        List<KbChunk> chunks = (List<KbChunk>) searchResult.get("records");

        StringBuilder context = new StringBuilder();
        List<Map<String, Object>> sources = new ArrayList<>();
        int index = 1;
        for (KbChunk chunk : chunks) {
            context.append("[").append(index).append("] 《")
                    .append(chunk.getDocumentTitle() == null ? "未命名文档" : chunk.getDocumentTitle())
                    .append("》");
            if (chunk.getArticleNo() != null) {
                context.append(" ").append(chunk.getArticleNo());
            }
            context.append("\n").append(chunk.getContent()).append("\n\n");

            Map<String, Object> source = new LinkedHashMap<>();
            source.put("index", index);
            source.put("documentId", chunk.getDocumentId());
            source.put("title", chunk.getDocumentTitle());
            source.put("articleNo", chunk.getArticleNo());
            source.put("content", chunk.getContent());
            source.put("score", chunk.getScore());
            sources.add(source);
            index++;
        }

        if (chunks.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("answer", "知识库中未检索到与问题相关的条文，请先上传法规文档或调整提问。");
            empty.put("sources", sources);
            empty.put("mode", searchResult.get("mode"));
            empty.put("cost", System.currentTimeMillis() - start);
            return empty;
        }

        String system = "你是 NCS RegTech 的监管合规助手。只能依据给定的法规条文回答，"
                + "回答需引用编号如 [1][2]；条文未覆盖的部分必须明确说明“现有条文未涵盖”。";
        String user = "请基于以下检索到的法规条文回答问题。\n\n条文：\n" + context
                + "\n问题：" + form.getQuestion();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", form.getModel());
        payload.put("temperature", 0.2);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        ));

        String answer;
        try {
            Map<String, Object> body = restClient.post()
                    .uri(aiBase + "/internal/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Object data = body == null ? null : body.get("data");
            answer = data instanceof Map<?, ?> map && map.get("content") != null
                    ? String.valueOf(map.get("content"))
                    : "模型未返回内容";
        } catch (Exception e) {
            answer = "生成回答失败（" + e.getMessage() + "）。已检索到 " + chunks.size() + " 条相关条文，请参考右侧来源。";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        result.put("sources", sources);
        result.put("mode", searchResult.get("mode"));
        result.put("cost", System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 补向量：为历史数据 / 初始化数据生成 embedding。
     * 切换向量模型或初始化导入后调用一次即可。
     */
    @Transactional
    public Map<String, Object> reindex(int limit) {
        int max = Math.max(1, Math.min(limit, 5000));
        int chunkUpdated = 0;
        int articleUpdated = 0;

        while (chunkUpdated < max) {
            List<KbChunk> chunks = kbMapper.selectChunksMissingEmbedding(32);
            if (chunks.isEmpty()) {
                break;
            }
            List<String> texts = chunks.stream().map(KbChunk::getContent).toList();
            List<List<Double>> vectors = embeddingClient.embedBatch(texts);
            for (int i = 0; i < chunks.size(); i++) {
                if (i < vectors.size() && !vectors.get(i).isEmpty()) {
                    kbMapper.updateChunkEmbedding(chunks.get(i).getId(),
                            EmbeddingClient.toVectorLiteral(vectors.get(i)));
                    chunkUpdated++;
                }
            }
        }

        while (articleUpdated < max) {
            List<LegalArticle> articles = kbMapper.selectArticlesMissingEmbedding(32);
            if (articles.isEmpty()) {
                break;
            }
            List<String> texts = articles.stream()
                    .map(a -> a.getLawName() + a.getArticleNo() + a.getContent())
                    .toList();
            List<List<Double>> vectors = embeddingClient.embedBatch(texts);
            for (int i = 0; i < articles.size(); i++) {
                if (i < vectors.size() && !vectors.get(i).isEmpty()) {
                    kbMapper.updateArticleEmbedding(articles.get(i).getId(),
                            EmbeddingClient.toVectorLiteral(vectors.get(i)));
                    articleUpdated++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chunks", chunkUpdated);
        result.put("articles", articleUpdated);
        return result;
    }

    // ---------------------------------------------------------------- 统计

    public Map<String, Object> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("datasets", kbMapper.countDatasets());
        data.put("documents", kbMapper.countDocuments(null, null));
        data.put("chunks", kbMapper.countChunks());
        data.put("articles", kbMapper.countArticles(null, null));
        data.put("diffs", kbMapper.countDiffs());
        data.put("jurisdictions", kbMapper.countArticlesByJurisdiction());
        return data;
    }

    // ---------------------------------------------------------------- 内部工具

    private LegalArticle toArticle(KbRequests.ArticleForm form) {
        LegalArticle article = new LegalArticle();
        article.setLawName(form.getLawName());
        article.setArticleNo(form.getArticleNo());
        article.setTitle(StringUtils.hasText(form.getTitle())
                ? form.getTitle() : form.getLawName() + " " + form.getArticleNo());
        article.setJurisdiction(form.getJurisdiction());
        article.setEffectiveDate(form.getEffectiveDate());
        article.setStatus(form.getStatus());
        article.setContent(form.getContent());
        article.setTags(form.getTags());
        article.setVersion(form.getVersion());
        return article;
    }

    private String embedOrNull(String text) {
        try {
            List<Double> vector = embeddingClient.embed(text);
            return vector.isEmpty() ? null : EmbeddingClient.toVectorLiteral(vector);
        } catch (Exception e) {
            log.warn("法条向量化失败，将只支持关键词检索：{}", e.getMessage());
            return null;
        }
    }

    private List<List<Double>> safeEmbed(List<String> pieces) {
        if (pieces.isEmpty()) {
            return List.of();
        }
        List<List<Double>> vectors = new ArrayList<>();
        int batch = 16;
        try {
            for (int i = 0; i < pieces.size(); i += batch) {
                vectors.addAll(embeddingClient.embedBatch(pieces.subList(i, Math.min(i + batch, pieces.size()))));
            }
        } catch (Exception e) {
            log.warn("文档向量化失败，本次仅入库不写向量：{}", e.getMessage());
        }
        return vectors;
    }

    /** 按段落聚合切分，超长再按窗口滑动切，保留上下文重叠 */
    List<String> split(String content) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : content.split("\\n+")) {
            String text = paragraph.trim();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > CHUNK_SIZE) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                int start = 0;
                while (start < text.length()) {
                    int end = Math.min(start + CHUNK_SIZE, text.length());
                    result.add(text.substring(start, end));
                    if (end == text.length()) {
                        break;
                    }
                    start = end - CHUNK_OVERLAP;
                }
                continue;
            }
            if (current.length() + text.length() + 1 > CHUNK_SIZE) {
                result.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(text);
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private static String detectArticleNo(String text) {
        Matcher matcher = ARTICLE_NO.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    /** 中文 3-gram 拆词：整句 ILIKE 命中不到时，用 3 字窗口做 OR 匹配并按命中率排序 */
    private static List<String> trigrams(String query) {
        String cleaned = query.replaceAll("[\\p{Punct}\\s，。；：、？！（）《》“”‘’·]", "");
        if (cleaned.length() < 3) {
            return cleaned.isEmpty() ? List.of() : List.of(cleaned);
        }
        Set<String> terms = new LinkedHashSet<>();
        for (int i = 0; i + 3 <= cleaned.length() && terms.size() < 40; i++) {
            terms.add(cleaned.substring(i, i + 3));
        }
        return new ArrayList<>(terms);
    }
}
