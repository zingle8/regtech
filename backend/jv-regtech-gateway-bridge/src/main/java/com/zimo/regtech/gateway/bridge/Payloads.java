package com.zimo.regtech.gateway.bridge;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上报模型：字段名与 ai-gateway 的 ServiceInstanceDTO / TraceLogDTO 一一对应。
 */
public final class Payloads {

    private Payloads() {
    }

    @Data
    public static class ServiceInstance {
        private String appId;
        private String appName;
        private String serviceName;
        private String ip;
        private Integer port;
        private String language;
        private String version;
        private LocalDateTime registerTime;
    }

    @Data
    public static class TraceLog {
        private String appId;
        private String appName;
        private String serviceName;
        private String api;
        private String reqParams;
        private String traceId;
        private String protocol;
        private String reqFormat;
        private String resFormat;
        private String reqIp;
        private Integer reqPort;
        private String resIp;
        private Integer resPort;
        private Integer statusCode;
        private String errType;
        private Integer cost;
        private LocalDateTime reqTime;
        private LocalDateTime resTime;
        private String reqHeader;
        private String resHeader;
        private String reqBody;
        private String resBody;
        private Long reqBytes;
        private Long resBytes;
    }
}
