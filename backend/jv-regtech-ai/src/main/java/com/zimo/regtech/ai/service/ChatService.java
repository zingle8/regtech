package com.zimo.regtech.ai.service;

import com.zimo.regtech.ai.client.OllamaClient;
import com.zimo.regtech.ai.entity.ChatMessage;
import com.zimo.regtech.ai.entity.Conversation;
import com.zimo.regtech.ai.mapper.AiMapper;
import com.zimo.regtech.ai.model.ChatRequest;
import com.zimo.regtech.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话编排：模型解析、上下文拼装、消息落库。
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int HISTORY_LIMIT = 20;

    private final AiMapper aiMapper;
    private final OllamaClient ollamaClient;

    @Value("${regtech.ai.ollama.default-model:gpt-oss:20b}")
    private String defaultModel;

    public String resolveModel(String model) {
        return StringUtils.hasText(model) ? model : defaultModel;
    }

    /** 取已有会话（校验归属），没有则新建 */
    public Conversation resolveConversation(Long userId, Long conversationId, String model, String scene, String firstText) {
        if (conversationId != null) {
            Conversation conversation = aiMapper.selectConversation(conversationId);
            if (conversation == null) {
                throw new BizException("会话不存在：" + conversationId);
            }
            return conversation;
        }
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setModel(model);
        conversation.setScene(scene);
        conversation.setTitle(title(firstText));
        aiMapper.insertConversation(conversation);
        return conversation;
    }

    /**
     * 拼装 Ollama 上下文：
     * 有历史会话时以库内历史为准，只补当前这一轮用户输入；
     * 无历史会话（无状态调用）时直接使用请求里的全部消息。
     */
    public List<Map<String, String>> buildMessages(ChatRequest request, Long conversationId) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }

        List<ChatMessage> history = conversationId == null
                ? List.of()
                : aiMapper.selectMessages(conversationId);

        if (history.isEmpty()) {
            for (ChatRequest.Message message : request.getMessages()) {
                if (StringUtils.hasText(message.getContent())) {
                    messages.add(Map.of(
                            "role", StringUtils.hasText(message.getRole()) ? message.getRole() : "user",
                            "content", message.getContent()));
                }
            }
            return messages;
        }

        int start = Math.max(0, history.size() - HISTORY_LIMIT);
        for (ChatMessage item : history.subList(start, history.size())) {
            messages.add(Map.of("role", item.getRole(), "content", item.getContent()));
        }

        ChatRequest.Message lastUser = lastUserMessage(request);
        if (lastUser != null) {
            Map<String, String> tail = messages.get(messages.size() - 1);
            boolean duplicated = "user".equals(tail.get("role")) && lastUser.getContent().equals(tail.get("content"));
            if (!duplicated) {
                messages.add(Map.of("role", "user", "content", lastUser.getContent()));
            }
        }
        return messages;
    }

    /** 当前这一轮的用户输入（请求里最后一条 user 消息） */
    public String currentUserText(ChatRequest request) {
        ChatRequest.Message message = lastUserMessage(request);
        return message == null ? "" : message.getContent();
    }

    private static ChatRequest.Message lastUserMessage(ChatRequest request) {
        List<ChatRequest.Message> list = request.getMessages();
        for (int i = list.size() - 1; i >= 0; i--) {
            ChatRequest.Message item = list.get(i);
            if (StringUtils.hasText(item.getContent()) && !"assistant".equals(item.getRole())) {
                return item;
            }
        }
        return null;
    }

    public void saveMessage(Long conversationId, String role, String content, Integer tokens, Integer costMs) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setTokens(tokens);
        message.setCostMs(costMs);
        aiMapper.insertMessage(message);
    }

    public void touch(Long conversationId, int count, String model) {
        aiMapper.touchConversation(conversationId, count, model);
    }

    /** OpenAI 兼容的非流式响应 */
    public Map<String, Object> openAiResponse(String model, String content, int promptTokens, int completionTokens) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", "chatcmpl-" + System.currentTimeMillis());
        response.put("object", "chat.completion");
        response.put("created", System.currentTimeMillis() / 1000);
        response.put("model", model);
        response.put("choices", List.of(Map.of(
                "index", 0,
                "message", Map.of("role", "assistant", "content", content),
                "finish_reason", "stop"
        )));
        response.put("usage", Map.of(
                "prompt_tokens", promptTokens,
                "completion_tokens", completionTokens,
                "total_tokens", promptTokens + completionTokens
        ));
        return response;
    }

    public OllamaClient ollama() {
        return ollamaClient;
    }

    public String supportedModelsHint() {
        return defaultModel;
    }

    private static String title(String text) {
        if (!StringUtils.hasText(text)) {
            return "新会话";
        }
        String cleaned = text.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 26 ? cleaned.substring(0, 26) + "..." : cleaned;
    }
}
