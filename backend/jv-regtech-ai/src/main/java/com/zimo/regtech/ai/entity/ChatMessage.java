package com.zimo.regtech.ai.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessage {

    private Long id;
    private Long conversationId;
    /** system / user / assistant */
    private String role;
    private String content;
    private Integer tokens;
    private Integer costMs;
    /** 引用来源（JSON 数组字符串） */
    private String sources;
    private LocalDateTime createTime;
}
