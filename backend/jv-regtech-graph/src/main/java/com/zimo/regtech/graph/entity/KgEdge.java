package com.zimo.regtech.graph.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KgEdge {

    private Long id;
    private Long sourceId;
    private Long targetId;
    private String sourceName;
    private String targetName;
    /** 包含 / 涉及 / 修订 / 处罚 / 引用 */
    private String relation;
    private Double weight;
    private LocalDateTime createTime;
}
