package com.zimo.regtech.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.regtech.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 本地 Ollama 客户端：把 OpenAI 协议翻译成 Ollama 原生协议（/api/chat、/api/embed）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${regtech.ai.ollama.base-url:http://127.0.0.1:11434}")
    private String baseUrl;

    @Value("${regtech.ai.ollama.keep-alive:30m}")
    private String keepAlive;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 本地已安装模型列表 */
    public List<Map<String, Object>> tags() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri(url("/api/tags"))
                    .retrieve()
                    .body(Map.class);
            Object models = body == null ? null : body.get("models");
            if (models instanceof List<?> list) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> model = new LinkedHashMap<>();
                        model.put("id", map.get("name"));
                        model.put("object", "model");
                        model.put("owned_by", "ollama");
                        model.put("size", map.get("size"));
                        model.put("modified_at", map.get("modified_at"));
                        model.put("details", map.get("details"));
                        result.add(model);
                    }
                }
                return result;
            }
            return List.of();
        } catch (Exception e) {
            throw new BizException("本地 Ollama 不可达（" + baseUrl + "）：" + e.getMessage());
        }
    }

    public boolean alive() {
        try {
            restClient.get().uri(url("/api/tags")).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 非流式对话 */
    @SuppressWarnings("unchecked")
    public ChatResult chat(String model, List<Map<String, String>> messages, double temperature) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("stream", false);
        payload.put("keep_alive", keepAlive);
        payload.put("options", Map.of("temperature", temperature));

        try {
            Map<String, Object> body = restClient.post()
                    .uri(url("/api/chat"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                throw new BizException("Ollama 返回为空");
            }
            Object message = body.get("message");
            String content = message instanceof Map<?, ?> map ? String.valueOf(map.get("content")) : "";
            int promptTokens = intOf(body.get("prompt_eval_count"));
            int completionTokens = intOf(body.get("eval_count"));
            return new ChatResult(content, promptTokens, completionTokens);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("调用 Ollama 失败：" + e.getMessage());
        }
    }

    /**
     * 流式对话（阻塞读取，由调用方放到独立线程）。
     */
    public void streamChat(String model, List<Map<String, String>> messages, double temperature,
                           Consumer<String> onDelta, Runnable onComplete) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("stream", true);
        payload.put("keep_alive", keepAlive);
        payload.put("options", Map.of("temperature", temperature));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url("/api/chat")))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new BizException("Ollama 返回状态码：" + response.statusCode());
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode node = objectMapper.readTree(line);
                    JsonNode content = node.path("message").path("content");
                    if (!content.isMissingNode() && !content.asText().isEmpty()) {
                        onDelta.accept(content.asText());
                    }
                    if (node.path("done").asBoolean(false)) {
                        break;
                    }
                }
            }
            onComplete.run();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Ollama 流式调用失败：" + e.getMessage());
        }
    }

    /**
     * 文本向量化：优先 /api/embed（支持批量），失败回落 /api/embeddings。
     */
    @SuppressWarnings("unchecked")
    public List<List<Double>> embeddings(String model, List<String> inputs) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("input", inputs);
            Map<String, Object> body = restClient.post()
                    .uri(url("/api/embed"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            Object embeddings = body == null ? null : body.get("embeddings");
            if (embeddings instanceof List<?> list) {
                List<List<Double>> result = new ArrayList<>();
                for (Object row : list) {
                    result.add(toDoubles((List<?>) row));
                }
                return result;
            }
        } catch (Exception e) {
            log.debug("/api/embed 不可用，回落到 /api/embeddings：{}", e.getMessage());
        }

        List<List<Double>> result = new ArrayList<>();
        for (String input : inputs) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("prompt", input);
            try {
                Map<String, Object> body = restClient.post()
                        .uri(url("/api/embeddings"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(Map.class);
                Object embedding = body == null ? null : body.get("embedding");
                result.add(embedding instanceof List<?> list ? toDoubles(list) : List.of());
            } catch (Exception e) {
                throw new BizException("生成向量失败（模型 " + model + "）：" + e.getMessage());
            }
        }
        return result;
    }

    private static List<Double> toDoubles(List<?> list) {
        List<Double> values = new ArrayList<>(list.size());
        for (Object item : list) {
            values.add(item == null ? 0d : Double.parseDouble(String.valueOf(item)));
        }
        return values;
    }

    private String url(String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + path;
    }

    private static int intOf(Object value) {
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    public record ChatResult(String content, int promptTokens, int completionTokens) {
    }
}
