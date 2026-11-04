package com.zimo.regtech.graph.service;

import com.zimo.regtech.common.exception.BizException;
import com.zimo.regtech.graph.entity.KgEdge;
import com.zimo.regtech.graph.entity.KgNode;
import com.zimo.regtech.graph.mapper.GraphMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 法律知识图谱：节点/关系维护，以及从法条库自动构图。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphService {

    private final GraphMapper graphMapper;

    /** 图谱数据（ECharts graph 格式） */
    public Map<String, Object> graph(String keyword, String category, int limit) {
        List<KgNode> nodes = graphMapper.selectNodes(keyword, category, Math.max(10, Math.min(limit, 800)));
        List<KgEdge> edges = graphMapper.selectEdges(2000);

        Set<Long> visible = new LinkedHashSet<>();
        nodes.forEach(node -> visible.add(node.getId()));

        // 命中节点的一跳邻居也一并展示，避免图谱被过滤成孤点
        if (StringUtils.hasText(keyword)) {
            for (KgEdge edge : edges) {
                if (visible.contains(edge.getSourceId()) || visible.contains(edge.getTargetId())) {
                    visible.add(edge.getSourceId());
                    visible.add(edge.getTargetId());
                }
            }
            if (visible.size() > nodes.size()) {
                List<KgNode> expanded = new ArrayList<>(nodes);
                Set<Long> existed = new LinkedHashSet<>(visible.stream().toList());
                existed.removeAll(nodes.stream().map(KgNode::getId).toList());
                for (Long id : existed) {
                    KgNode node = graphMapper.selectNode(id);
                    if (node != null) {
                        expanded.add(node);
                    }
                }
                nodes = expanded;
            }
        }

        Map<String, Integer> categoryIndex = new LinkedHashMap<>();
        List<Map<String, Object>> categories = new ArrayList<>();
        for (KgNode node : nodes) {
            String name = StringUtils.hasText(node.getCategory()) ? node.getCategory() : "其他";
            if (!categoryIndex.containsKey(name)) {
                categoryIndex.put(name, categories.size());
                categories.add(Map.of("name", name));
            }
        }

        Map<Long, Integer> degrees = new LinkedHashMap<>();
        List<Map<String, Object>> links = new ArrayList<>();
        for (KgEdge edge : edges) {
            if (!visible.contains(edge.getSourceId()) || !visible.contains(edge.getTargetId())) {
                continue;
            }
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("source", String.valueOf(edge.getSourceId()));
            link.put("target", String.valueOf(edge.getTargetId()));
            link.put("relation", edge.getRelation());
            link.put("value", edge.getWeight() == null ? 1 : edge.getWeight());
            links.add(link);
            degrees.merge(edge.getSourceId(), 1, Integer::sum);
            degrees.merge(edge.getTargetId(), 1, Integer::sum);
        }

        List<Map<String, Object>> nodeList = new ArrayList<>();
        for (KgNode node : nodes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(node.getId()));
            item.put("name", node.getName());
            item.put("category", categoryIndex.getOrDefault(
                    StringUtils.hasText(node.getCategory()) ? node.getCategory() : "其他", 0));
            item.put("categoryName", node.getCategory());
            item.put("label", node.getLabel());
            item.put("jurisdiction", node.getJurisdiction());
            item.put("description", node.getDescription());
            int degree = degrees.getOrDefault(node.getId(), 1);
            item.put("symbolSize", Math.min(46, 14 + degree * 2));
            item.put("value", degree);
            nodeList.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodeList);
        result.put("links", links);
        result.put("categories", categories);
        result.put("nodeTotal", nodeList.size());
        result.put("linkTotal", links.size());
        return result;
    }

    public List<KgNode> nodes(String keyword, String category) {
        return graphMapper.selectNodes(keyword, category, 300);
    }

    public KgNode node(Long id) {
        KgNode node = graphMapper.selectNode(id);
        if (node == null) {
            throw new BizException("节点不存在");
        }
        return node;
    }

    public KgNode createNode(KgNode node) {
        if (!StringUtils.hasText(node.getCode())) {
            node.setCode("node-" + System.currentTimeMillis());
        }
        KgNode exists = graphMapper.selectNodeByCode(node.getCode());
        if (exists != null) {
            throw new BizException("节点编码已存在：" + node.getCode());
        }
        node.setProperties(node.getProperties() == null ? "{}" : node.getProperties());
        graphMapper.insertNode(node);
        return graphMapper.selectNodeByCode(node.getCode());
    }

    public void updateNode(Long id, KgNode node) {
        node(id);
        node.setId(id);
        graphMapper.updateNode(node);
    }

    @Transactional
    public void deleteNode(Long id) {
        graphMapper.deleteEdgesByNode(id);
        graphMapper.deleteNode(id);
    }

    public KgEdge createEdge(KgEdge edge) {
        if (edge.getSourceId() == null || edge.getTargetId() == null) {
            throw new BizException("请选择关系的起点与终点");
        }
        node(edge.getSourceId());
        node(edge.getTargetId());
        edge.setRelation(StringUtils.hasText(edge.getRelation()) ? edge.getRelation() : "关联");
        edge.setWeight(edge.getWeight() == null ? 1d : edge.getWeight());
        graphMapper.insertEdge(edge);
        return edge;
    }

    public void deleteEdge(Long id) {
        graphMapper.deleteEdge(id);
    }

    /**
     * 从法条库自动构图：法规 → 条款 → 主题。
     */
    @Transactional
    public Map<String, Object> build(int limit) {
        List<Map<String, Object>> articles = graphMapper.selectArticlesForGraph(Math.max(1, Math.min(limit, 500)));
        graphMapper.clearEdges();
        graphMapper.clearNodes();

        int nodeCount = 0;
        int edgeCount = 0;
        for (Map<String, Object> article : articles) {
            String lawName = str(article.get("law_name"));
            String code = str(article.get("code"));
            String articleNo = str(article.get("article_no"));
            String jurisdiction = str(article.get("jurisdiction"));
            if (!StringUtils.hasText(lawName)) {
                continue;
            }

            Long lawId = upsert("law:" + lawName, lawName, lawName, "法规", jurisdiction,
                    "法规：" + lawName);
            Long articleId = upsert("art:" + code, lawName + " " + articleNo,
                    str(article.get("title")), "条款", jurisdiction,
                    "《" + lawName + "》" + articleNo);
            if (lawId != null && articleId != null) {
                edgeCount += link(lawId, articleId, "包含");
            }

            String tags = str(article.get("tags"));
            if (StringUtils.hasText(tags)) {
                for (String tag : tags.split("[,，;；]")) {
                    String name = tag.trim();
                    if (name.isEmpty()) {
                        continue;
                    }
                    Long tagId = upsert("tag:" + name, name, name, "主题", jurisdiction, "监管主题：" + name);
                    if (tagId != null && articleId != null) {
                        edgeCount += link(articleId, tagId, "涉及");
                    }
                }
            }
            nodeCount += 2;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("articles", articles.size());
        result.put("nodes", graphMapper.countNodes());
        result.put("edges", graphMapper.countEdges());
        result.put("processedNodes", nodeCount);
        result.put("processedEdges", edgeCount);
        log.info("知识图谱重建完成：{} 条法条 → {} 节点 / {} 关系", articles.size(),
                result.get("nodes"), result.get("edges"));
        return result;
    }

    public Map<String, Object> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodes", graphMapper.countNodes());
        data.put("edges", graphMapper.countEdges());
        data.put("categories", graphMapper.countByCategory());
        return data;
    }

    private Long upsert(String code, String name, String label, String category,
                        String jurisdiction, String description) {
        KgNode exists = graphMapper.selectNodeByCode(code);
        if (exists != null) {
            return exists.getId();
        }
        KgNode node = new KgNode();
        node.setCode(code);
        node.setName(name);
        node.setLabel(label);
        node.setCategory(category);
        node.setJurisdiction(jurisdiction);
        node.setDescription(description);
        node.setProperties("{}");
        graphMapper.insertNode(node);
        KgNode created = graphMapper.selectNodeByCode(code);
        return created == null ? null : created.getId();
    }

    private int link(Long sourceId, Long targetId, String relation) {
        KgEdge edge = new KgEdge();
        edge.setSourceId(sourceId);
        edge.setTargetId(targetId);
        edge.setRelation(relation);
        edge.setWeight(1d);
        return graphMapper.insertEdge(edge);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
