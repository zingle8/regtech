package com.zimo.regtech.graph.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KgNode {

    private Long id;
    private String code;
    private String name;
    private String label;
    /** 法规 / 条款 / 主题 / 机构 / 罚则 */
    private String category;
    private String jurisdiction;
    private String description;
    private String properties;
    private LocalDateTime createTime;
}
