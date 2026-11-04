package com.zimo.regtech.ontology.entity;

import lombok.Data;

@Data
public class OntologyProperty {

    private Long id;
    private Long classId;
    private String code;
    private String name;
    /** string / number / date / boolean / ref */
    private String dataType;
    private Boolean required;
    private String description;
}
