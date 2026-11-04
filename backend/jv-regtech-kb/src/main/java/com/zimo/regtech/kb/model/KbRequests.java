package com.zimo.regtech.kb.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 请求表单集合（按业务归拢，避免十几个单字段文件）。
 */
public final class KbRequests {

    private KbRequests() {
    }

    @Data
    public static class DatasetForm {
        private String code;
        @NotBlank(message = "请填写知识库名称")
        private String name;
        private String description;
        private String jurisdiction = "CN";
        private String embeddingModel;
    }

    @Data
    public static class DocumentForm {
        private Long datasetId;
        @NotBlank(message = "请填写文档标题")
        private String title;
        /** 原文粘贴 / 上传文件 */
        private String sourceType = "TEXT";
        private String jurisdiction = "CN";
        private LocalDate effectiveDate;
        private String status = "现行有效";
        @NotBlank(message = "请填写文档内容")
        private String content;
        /** 是否同时写入法条库 */
        private boolean syncArticles = false;
    }

    @Data
    public static class ArticleForm {
        private String code;
        @NotBlank(message = "请填写法规名称")
        private String lawName;
        @NotBlank(message = "请填写条款号")
        private String articleNo;
        private String title;
        private String jurisdiction = "CN";
        private LocalDate effectiveDate;
        private String status = "现行有效";
        @NotBlank(message = "请填写条款正文")
        private String content;
        private String tags;
        private String version = "v1";
    }

    @Data
    public static class SearchForm {
        private String query;
        private Long datasetId;
        private Long documentId;
        private int topK = 8;
        /** vector / keyword */
        private String mode = "vector";
    }

    @Data
    public static class RagForm {
        @NotBlank(message = "请输入问题")
        private String question;
        private Long datasetId;
        private int topK = 6;
        private String model;
    }

    @Data
    public static class DiffForm {
        private Long leftId;
        private Long rightId;
        private String changeType;
    }

    /**
     * 多地域 / 多场景条文对比：
     * 场景（法律主题，如“投资者适当性”）× 法域（CN/HK/TW…）交叉打分。
     */
    @Data
    public static class MatrixForm {
        /** 法域列表，空则取库中全部法域 */
        private List<String> jurisdictions;
        /** 场景 / 主题列表，如 ["投资者适当性", "信息披露"] */
        private List<String> scenarios;
        /** 每个法域召回的条文数（1~5） */
        private int topK = 2;
        /** 是否调用本地模型生成差异解读 */
        private boolean explain = false;
        private String model;
    }
}
