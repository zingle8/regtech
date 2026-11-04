package com.zimo.regtech.kb.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class KbDocument {

    private Long id;
    private Long datasetId;
    private String datasetName;
    private String title;
    private String sourceType;
    private String jurisdiction;
    private LocalDate effectiveDate;
    private String status;
    private String content;
    private Integer charCount;
    private Integer chunkCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
