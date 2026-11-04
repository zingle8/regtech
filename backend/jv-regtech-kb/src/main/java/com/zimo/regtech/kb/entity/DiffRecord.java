package com.zimo.regtech.kb.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DiffRecord {

    private Long id;
    private Long leftId;
    private Long rightId;
    private String leftTitle;
    private String rightTitle;
    private String changeType;
    private Double similarity;
    private String diffJson;
    private LocalDateTime createTime;
}
