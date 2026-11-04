package com.zimo.regtech.ontology.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OntologyClass {

    private Long id;
    private String code;
    private String name;
    private Long parentId;
    private String description;
    private String jurisdiction;
    private LocalDateTime createTime;
    /** 组装树时使用 */
    private List<OntologyClass> children;
}
