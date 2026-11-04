package com.zimo.regtech.ai.model;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * OpenAI 兼容的对话请求（额外支持 conversationId / scene 用于会话持久化）。
 */
@Data
public class ChatRequest {

    @NotEmpty(message = "messages 不能为空")
    private List<Message> messages;

    private String model;

    private Double temperature = 0.7d;

    private boolean stream = false;

    private Long conversationId;

    private String scene = "chat";

    /** 系统提示词（可选，前端按场景注入） */
    private String systemPrompt;

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}
