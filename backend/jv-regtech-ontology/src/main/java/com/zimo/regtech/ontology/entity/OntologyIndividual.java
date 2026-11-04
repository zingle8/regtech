package com.zimo.regtech.ontology.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OntologyIndividual {

    private Long id;
    private Long classId;
    private String className;
    private String name;
    private String props;
    private String description;
    private LocalDateTime createTime;
}
