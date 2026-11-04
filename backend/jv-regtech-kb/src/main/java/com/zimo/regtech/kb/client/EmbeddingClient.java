package com.zimo.regtech.kb.client;

import com.zimo.regtech.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用 jv-regtech-ai 的向量化接口（内部接口，无需登录）。
 *
 * <p>AI 服务把请求翻译成 Ollama /api/embed，返回 1024 维向量（qwen3-embedding:0.6b）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    private final RestClient restClient;

    @Value("${regtech.services.ai:http://127.0.0.1:28105/api/regtech/ai}")
    private String aiBase;

    @Value("${regtech.kb.embedding-model:qwen3-embedding:0.6b}")
    private String embeddingModel;

    public List<Double> embed(String text) {
        List<List<Double>> vectors = embedBatch(List.of(text));
        return vectors.isEmpty() ? List.of() : vectors.get(0);
    }

    @SuppressWarnings("unchecked")
    public List<List<Double>> embedBatch(List<String> texts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", embeddingModel);
        payload.put("input", texts);
        try {
            Map<String, Object> body = restClient.post()
                    .uri(aiBase + "/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Object data = body == null ? null : body.get("data");
            if (data instanceof Map<?, ?> map) {
                Object rows = map.get("data");
                if (rows instanceof List<?> list) {
                    List<List<Double>> result = new ArrayList<>();
                    for (Object row : list) {
                        if (row instanceof Map<?, ?> item && item.get("embedding") instanceof List<?> values) {
                            List<Double> vector = new ArrayList<>(values.size());
                            for (Object value : values) {
                                vector.add(Double.parseDouble(String.valueOf(value)));
                            }
                            result.add(vector);
                        }
                    }
                    return result;
                }
            }
            throw new BizException("向量服务返回结构异常");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("向量化失败（请确认 Ollama 已安装 " + embeddingModel + "）：" + e.getMessage());
        }
    }

    public String model() {
        return embeddingModel;
    }

    /** 转成 pgvector 的文本字面量：[0.1,0.2,...] */
    public static String toVectorLiteral(List<Double> vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector.get(i));
        }
        return sb.append(']').toString();
    }
}
