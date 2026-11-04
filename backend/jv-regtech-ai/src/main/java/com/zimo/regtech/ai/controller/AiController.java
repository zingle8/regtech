package com.zimo.regtech.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.regtech.ai.client.OllamaClient;
import com.zimo.regtech.ai.entity.Conversation;
import com.zimo.regtech.ai.mapper.AiMapper;
import com.zimo.regtech.ai.model.ChatRequest;
import com.zimo.regtech.ai.service.ChatService;
import com.zimo.regtech.common.context.UserContext;
import com.zimo.regtech.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI 兼容入口 + 本地 Ollama 能力。
 *
 * <ul>
 *   <li>GET  /models                 本地模型列表（OpenAI 格式）</li>
 *   <li>POST /v1/chat/completions    非流式对话</li>
 *   <li>POST /v1/chat/completions/stream 流式对话（SSE）</li>
 *   <li>POST /internal/chat          服务内部调用（知识库 / 本体 RAG）</li>
 *   <li>POST /embeddings             文本向量化（内部调用）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AiController {

    private final ChatService chatService;
    private final OllamaClient ollamaClient;
    private final AiMapper aiMapper;
    private final ObjectMapper objectMapper;

    @Value("${regtech.ai.ollama.embedding-model:qwen3-embedding:0.6b}")
    private String embeddingModel;

    // ------------------------------------------------------------------ 模型

    @GetMapping({"/models", "/v1/models"})
    public R<Map<String, Object>> models() {
        List<Map<String, Object>> models;
        try {
            models = ollamaClient.tags();
        } catch (Exception e) {
            models = List.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("object", "list");
        data.put("data", models);
        data.put("defaultModel", chatService.supportedModelsHint());
        data.put("embeddingModel", embeddingModel);
        data.put("online", ollamaClient.alive());
        return R.ok(data);
    }

    @GetMapping("/health/ollama")
    public R<Map<String, Object>> ollamaHealth() {
        boolean alive = ollamaClient.alive();
        return R.ok(Map.of("online", alive, "status", alive ? "UP" : "DOWN"));
    }

    // ------------------------------------------------------------------ 对话

    @PostMapping("/v1/chat/completions")
    public Map<String, Object> completions(@Valid @RequestBody ChatRequest request) {
        Long userId = UserContext.getUserId();
        String model = chatService.resolveModel(request.getModel());
        Conversation conversation = chatService.resolveConversation(
                userId, request.getConversationId(), model, request.getScene(),
                chatService.currentUserText(request));

        List<Map<String, String>> messages = chatService.buildMessages(request, conversation.getId());
        String userText = chatService.currentUserText(request);
        chatService.saveMessage(conversation.getId(), "user", userText, null, null);

        long start = System.currentTimeMillis();
        OllamaClient.ChatResult result = ollamaClient.chat(model, messages,
                request.getTemperature() == null ? 0.7d : request.getTemperature());
        int cost = (int) (System.currentTimeMillis() - start);

        chatService.saveMessage(conversation.getId(), "assistant", result.content(),
                result.completionTokens(), cost);
        chatService.touch(conversation.getId(), 2, model);

        Map<String, Object> response = chatService.openAiResponse(
                model, result.content(), result.promptTokens(), result.completionTokens());
        response.put("conversationId", conversation.getId());
        return response;
    }

    @PostMapping(value = "/v1/chat/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter completionsStream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        Long userId = UserContext.getUserId();

        CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            try {
                String model = chatService.resolveModel(request.getModel());
                String userText = chatService.currentUserText(request);
                Conversation conversation = chatService.resolveConversation(
                        userId, request.getConversationId(), model, request.getScene(), userText);

                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(Map.of(
                        "type", "meta",
                        "conversationId", conversation.getId(),
                        "model", model
                ))));

                List<Map<String, String>> messages = chatService.buildMessages(request, conversation.getId());
                chatService.saveMessage(conversation.getId(), "user", userText, null, null);

                StringBuilder answer = new StringBuilder();
                double temperature = request.getTemperature() == null ? 0.7d : request.getTemperature();
                chatService.ollama().streamChat(model, messages, temperature, delta -> {
                    answer.append(delta);
                    try {
                        Map<String, Object> chunk = new LinkedHashMap<>();
                        chunk.put("id", "chatcmpl-stream");
                        chunk.put("object", "chat.completion.chunk");
                        chunk.put("created", System.currentTimeMillis() / 1000);
                        chunk.put("model", model);
                        chunk.put("choices", List.of(Map.of(
                                "index", 0,
                                "delta", Map.of("content", delta),
                                "finish_reason", ""
                        )));
                        chunk.put("type", "delta");
                        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(chunk)));
                    } catch (Exception ignored) {
                        // 客户端断开时忽略
                    }
                }, () -> {
                });

                int cost = (int) (System.currentTimeMillis() - start);
                chatService.saveMessage(conversation.getId(), "assistant", answer.toString(), null, cost);
                chatService.touch(conversation.getId(), 2, model);

                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                log.warn("流式对话失败：{}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(Map.of(
                            "type", "error",
                            "message", e.getMessage() == null ? "模型调用失败" : e.getMessage()
                    ))));
                } catch (Exception ignored) {
                    // 已断开
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /** 供知识库 / 本体 RAG 内部调用（不需要登录） */
    @PostMapping("/internal/chat")
    public R<Map<String, Object>> internalChat(@RequestBody ChatRequest request) {
        String model = chatService.resolveModel(request.getModel());
        List<Map<String, String>> messages = chatService.buildMessages(request, null);
        OllamaClient.ChatResult result = ollamaClient.chat(model, messages,
                request.getTemperature() == null ? 0.2d : request.getTemperature());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", result.content());
        data.put("model", model);
        data.put("promptTokens", result.promptTokens());
        data.put("completionTokens", result.completionTokens());
        return R.ok(data);
    }

    // ------------------------------------------------------------------ 向量

    @PostMapping({"/embeddings", "/v1/embeddings"})
    public R<Map<String, Object>> embeddings(@RequestBody Map<String, Object> body) {
        Object raw = body.get("input");
        List<String> inputs = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                inputs.add(String.valueOf(item));
            }
        } else if (raw != null) {
            inputs.add(String.valueOf(raw));
        }
        if (inputs.isEmpty()) {
            return R.fail("input 不能为空");
        }
        String model = body.get("model") == null ? embeddingModel : String.valueOf(body.get("model"));
        List<List<Double>> vectors = ollamaClient.embeddings(model, inputs);

        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("object", "embedding");
            item.put("index", i);
            item.put("embedding", vectors.get(i));
            data.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("object", "list");
        result.put("model", model);
        result.put("dimension", vectors.isEmpty() ? 0 : vectors.get(0).size());
        result.put("data", data);
        return R.ok(result);
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversations", aiMapper.countConversations());
        data.put("messages", aiMapper.countMessages());
        data.put("todayMessages", aiMapper.countTodayMessages());
        data.put("byModel", aiMapper.countByModel());
        data.put("ollamaOnline", ollamaClient.alive());
        return R.ok(data);
    }
}
