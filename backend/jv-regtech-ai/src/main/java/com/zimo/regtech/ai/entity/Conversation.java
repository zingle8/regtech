package com.zimo.regtech.ai.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Conversation {

    private Long id;
    private Long userId;
    private String title;
    private String model;
    /** 场景：chat / kb-rag / ontology-rag / diff */
    private String scene;
    private Integer messageCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
