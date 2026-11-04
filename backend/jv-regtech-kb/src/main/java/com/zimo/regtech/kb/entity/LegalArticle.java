package com.zimo.regtech.kb.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class LegalArticle {

    private Long id;
    private String code;
    private String lawName;
    private String articleNo;
    private String title;
    private String jurisdiction;
    private LocalDate effectiveDate;
    /** 现行有效 / 已修订 / 已废止 */
    private String status;
    private String content;
    private String tags;
    private String version;
    private Double score;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
