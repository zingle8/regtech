package com.zimo.regtech.overview.service;

import com.zimo.regtech.overview.mapper.OverviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 总览聚合：本服务读 MySQL，其余指标按需调用各微服务 /stats（失败即降级为 0）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverviewService {

    private final OverviewMapper overviewMapper;
    private final RestClient restClient;

    @Value("${regtech.services.kb:http://127.0.0.1:28102/api/regtech/kb}")
    private String kbBase;

    @Value("${regtech.services.graph:http://127.0.0.1:28103/api/regtech/graph}")
    private String graphBase;

    @Value("${regtech.services.ontology:http://127.0.0.1:28104/api/regtech/ontology}")
    private String ontologyBase;

    @Value("${regtech.services.ai:http://127.0.0.1:28105/api/regtech/ai}")
    private String aiBase;

    public Map<String, Object> summary() {
        Map<String, Object> kb = remote(kbBase + "/stats");
        Map<String, Object> graph = remote(graphBase + "/stats");
        Map<String, Object> ontology = remote(ontologyBase + "/stats");
        Map<String, Object> ai = remote(aiBase + "/stats");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users", overviewMapper.countUsers());
        data.put("activeUsers", overviewMapper.countActiveUsers());
        data.put("conversations", overviewMapper.countConversations());
        data.put("messages", overviewMapper.countMessages());
        data.put("todayMessages", overviewMapper.countTodayMessages());

        data.put("kbDatasets", number(kb, "datasets"));
        data.put("kbDocuments", number(kb, "documents"));
        data.put("kbChunks", number(kb, "chunks"));
        data.put("legalArticles", number(kb, "articles"));
        data.put("diffRecords", number(kb, "diffs"));

        data.put("graphNodes", number(graph, "nodes"));
        data.put("graphEdges", number(graph, "edges"));

        data.put("ontologyClasses", number(ontology, "classes"));
        data.put("ontologyIndividuals", number(ontology, "individuals"));

        data.put("ollamaOnline", Boolean.TRUE.equals(ai.get("ollamaOnline")));
        data.put("jurisdictions", kb.getOrDefault("jurisdictions", List.of()));
        data.put("roleDistribution", overviewMapper.roleDistribution());
        return data;
    }

    /** 近 N 天问答量趋势（无数据日期补 0） */
    public List<Map<String, Object>> trend(int days) {
        int window = Math.max(1, Math.min(days, 90));
        List<Map<String, Object>> rows = overviewMapper.messageTrend(window);
        Map<String, Long> indexed = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            indexed.put(String.valueOf(row.get("day")), toLong(row.get("total")));
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = window - 1; i >= 0; i--) {
            String day = today.minusDays(i).format(formatter);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("day", day);
            point.put("total", indexed.getOrDefault(day, 0L));
            result.add(point);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> remote(String url) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Object data = body == null ? null : body.get("data");
            return data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (Exception e) {
            log.debug("读取 {} 统计失败：{}", url, e.getMessage());
            return Map.of();
        }
    }

    private static long number(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? 0L : toLong(value);
    }

    private static long toLong(Object value) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0L;
        }
    }
}
