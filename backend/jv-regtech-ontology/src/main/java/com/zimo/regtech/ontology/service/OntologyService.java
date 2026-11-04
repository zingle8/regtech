package com.zimo.regtech.ontology.service;

import com.zimo.regtech.common.exception.BizException;
import com.zimo.regtech.ontology.entity.OntologyClass;
import com.zimo.regtech.ontology.entity.OntologyIndividual;
import com.zimo.regtech.ontology.entity.OntologyProperty;
import com.zimo.regtech.ontology.mapper.OntologyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本体论 RAG：用本体概念扩展检索词，再把命中的本体结构与法条一起交给模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OntologyService {

    private final OntologyMapper ontologyMapper;
    private final RestClient restClient;

    @Value("${regtech.services.kb:http://127.0.0.1:28102/api/regtech/kb}")
    private String kbBase;

    @Value("${regtech.services.ai:http://127.0.0.1:28105/api/regtech/ai}")
    private String aiBase;

    // ---------------------------------------------------------------- 本体类

    public List<OntologyClass> tree(String keyword) {
        List<OntologyClass> flat = ontologyMapper.selectClasses(keyword);
        Map<Long, OntologyClass> index = new LinkedHashMap<>();
        flat.forEach(item -> index.put(item.getId(), item));
        List<OntologyClass> roots = new ArrayList<>();
        for (OntologyClass item : flat) {
            if (item.getParentId() == null || !index.containsKey(item.getParentId())) {
                roots.add(item);
            } else {
                OntologyClass parent = index.get(item.getParentId());
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(item);
            }
        }
        return roots;
    }

    public OntologyClass createClass(OntologyClass clazz) {
        if (!StringUtils.hasText(clazz.getName())) {
            throw new BizException("请填写本体类名称");
        }
        clazz.setCode(StringUtils.hasText(clazz.getCode())
                ? clazz.getCode() : "cls-" + System.currentTimeMillis());
        if (ontologyMapper.selectClassByCode(clazz.getCode()) != null) {
            throw new BizException("本体类编码已存在：" + clazz.getCode());
        }
        ontologyMapper.insertClass(clazz);
        return ontologyMapper.selectClass(clazz.getId());
    }

    public void updateClass(Long id, OntologyClass clazz) {
        clazz.setId(id);
        ontologyMapper.updateClass(clazz);
    }

    public void deleteClass(Long id) {
        if (ontologyMapper.countChildren(id) > 0) {
            throw new BizException("该本体类下还有子类，请先删除子类");
        }
        ontologyMapper.deleteClass(id);
    }

    public List<OntologyProperty> properties(Long classId) {
        return ontologyMapper.selectProperties(classId);
    }

    public OntologyProperty createProperty(OntologyProperty property) {
        if (property.getClassId() == null) {
            throw new BizException("请选择所属本体类");
        }
        ontologyMapper.insertProperty(property);
        return property;
    }

    public void deleteProperty(Long id) {
        ontologyMapper.deleteProperty(id);
    }

    public List<OntologyIndividual> individuals(Long classId, String keyword) {
        return ontologyMapper.selectIndividuals(classId, keyword);
    }

    public OntologyIndividual createIndividual(OntologyIndividual individual) {
        if (individual.getClassId() == null || !StringUtils.hasText(individual.getName())) {
            throw new BizException("请填写所属类与个体名称");
        }
        individual.setProps(StringUtils.hasText(individual.getProps()) ? individual.getProps() : "{}");
        ontologyMapper.insertIndividual(individual);
        return individual;
    }

    public void updateIndividual(Long id, OntologyIndividual individual) {
        individual.setId(id);
        ontologyMapper.updateIndividual(individual);
    }

    public void deleteIndividual(Long id) {
        ontologyMapper.deleteIndividual(id);
    }

    // ---------------------------------------------------------------- 本体 RAG

    @SuppressWarnings("unchecked")
    public Map<String, Object> rag(String question, int topK) {
        if (!StringUtils.hasText(question)) {
            throw new BizException("请输入问题");
        }

        List<OntologyClass> classes = ontologyMapper.selectClasses(null);
        List<OntologyIndividual> individuals = ontologyMapper.selectIndividuals(null, null);

        List<Map<String, Object>> hits = new ArrayList<>();
        Set<String> expansions = new LinkedHashSet<>();

        for (OntologyClass clazz : classes) {
            if (matches(question, clazz.getName(), clazz.getDescription())) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("type", "class");
                hit.put("id", clazz.getId());
                hit.put("name", clazz.getName());
                hit.put("description", clazz.getDescription());
                hits.add(hit);
                expansions.add(clazz.getName());
                for (OntologyClass child : classes) {
                    if (clazz.getId().equals(child.getParentId())) {
                        expansions.add(child.getName());
                        hit.put("children", true);
                    }
                }
            }
        }
        for (OntologyIndividual individual : individuals) {
            if (matches(question, individual.getName(), individual.getDescription())) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("type", "individual");
                hit.put("id", individual.getId());
                hit.put("name", individual.getName());
                hit.put("className", individual.getClassName());
                hits.add(hit);
                expansions.add(individual.getName());
            }
        }

        String enriched = expansions.isEmpty()
                ? question
                : question + "（相关概念：" + String.join("、", expansions) + "）";

        List<Map<String, Object>> sources = new ArrayList<>();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("query", enriched);
            payload.put("topK", Math.max(1, Math.min(topK, 12)));
            Map<String, Object> body = restClient.post()
                    .uri(kbBase + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Object data = body == null ? null : body.get("data");
            if (data instanceof Map<?, ?> map && map.get("records") instanceof List<?> records) {
                int index = 1;
                for (Object row : records) {
                    if (row instanceof Map<?, ?> item) {
                        Map<String, Object> source = new LinkedHashMap<>();
                        source.put("index", index++);
                        source.put("title", item.get("documentTitle"));
                        source.put("articleNo", item.get("articleNo"));
                        source.put("content", item.get("content"));
                        source.put("score", item.get("score"));
                        sources.add(source);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("本体 RAG 检索知识库失败：{}", e.getMessage());
        }

        String answer;
        if (sources.isEmpty()) {
            answer = "未在知识库中检索到相关条文。可先在「法律知识库」上传法规文档，或补充本体个体后重试。";
        } else {
            StringBuilder context = new StringBuilder();
            for (Map<String, Object> source : sources) {
                context.append("[").append(source.get("index")).append("] ")
                        .append(source.get("title") == null ? "" : source.get("title"))
                        .append(" ").append(source.get("articleNo") == null ? "" : source.get("articleNo"))
                        .append("\n").append(source.get("content")).append("\n\n");
            }
            StringBuilder schema = new StringBuilder();
            for (OntologyClass clazz : classes) {
                schema.append("- ").append(clazz.getName());
                if (StringUtils.hasText(clazz.getDescription())) {
                    schema.append("：").append(clazz.getDescription());
                }
                schema.append('\n');
            }

            String system = "你是 NCS RegTech 本体驱动的合规助手。已知本体结构如下：\n" + schema
                    + "\n请结合本体定义与检索到的法规条文回答，引用编号如 [1][2]；"
                    + "条文未覆盖的部分必须说明“现有条文未涵盖”。";
            String user = "检索到的法规条文：\n" + context + "\n问题：" + question;

            Map<String, Object> payload = new LinkedHashMap<>();
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
                answer = data instanceof Map<?, ?> map && map.get("content") != null
                        ? String.valueOf(map.get("content"))
                        : "模型未返回内容";
            } catch (Exception e) {
                answer = "生成回答失败（" + e.getMessage() + "）。已检索到 " + sources.size() + " 条相关条文。";
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        result.put("sources", sources);
        result.put("ontologyHits", hits);
        result.put("expansions", new ArrayList<>(expansions));
        result.put("enrichedQuery", enriched);
        return result;
    }

    public Map<String, Object> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("classes", ontologyMapper.countClasses());
        data.put("properties", ontologyMapper.countProperties());
        data.put("individuals", ontologyMapper.countIndividuals());
        return data;
    }

    private static boolean matches(String question, String name, String description) {
        if (!StringUtils.hasText(name) || name.length() < 2) {
            return false;
        }
        return question.contains(name)
                || (StringUtils.hasText(description) && description.contains(question.trim()));
    }
}
