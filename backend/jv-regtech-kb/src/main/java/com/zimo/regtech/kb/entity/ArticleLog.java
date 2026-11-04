package com.zimo.regtech.kb.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 法律条文变更日志：新增 / 更新 / 删除留痕，供总览页「条文变更动态」展示。
 */
@Data
public class ArticleLog {

    /** 变更类型 */
    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";

    private Long id;
    private Long articleId;
    /** CREATE / UPDATE / DELETE */
    private String action;
    private String code;
    private String lawName;
    private String articleNo;
    private String title;
    /** 变更摘要，如「更新 正文、效力状态」 */
    private String summary;
    private String operatorName;
    private LocalDateTime operateTime;
}
