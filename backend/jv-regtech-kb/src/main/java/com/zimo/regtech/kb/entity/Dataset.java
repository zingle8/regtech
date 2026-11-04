package com.zimo.regtech.kb.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Dataset {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String jurisdiction;
    private String embeddingModel;
    private Integer docCount;
    private Integer chunkCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
