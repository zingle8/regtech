package com.zimo.regtech.kb.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KbChunk {

    private Long id;
    private Long datasetId;
    private Long documentId;
    private String documentTitle;
    private Integer chunkNo;
    private String articleNo;
    private String content;
    private Integer tokens;
    private Double score;
    private LocalDateTime createTime;
}
