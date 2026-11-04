package com.zimo.regtech.gateway.bridge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关平台接入配置（协议与 ai-gateway 的 jv-gateway-client-starter 完全一致）。
 *
 * <pre>
 * regtech:
 *   gateway:
 *     enabled: true
 *     base-url: http://127.0.0.1:28090/api
 *     app-id: app-regtech
 *     app-name: NCS RegTech
 *     service-name: jv-regtech-kb
 *     port: 28102
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "regtech.gateway")
public class GatewayBridgeProperties {

    /** 总开关 */
    private boolean enabled = true;

    /** 平台统一入口：指向 jv-gateway-proxy（28090）或前端 dev server 的 /api */
    private String baseUrl = "http://127.0.0.1:28090/api";

    private String appId = "app-regtech";

    private String appName = "NCS RegTech 监管科技";

    private String serviceName = "regtech-service";

    private String ip = "127.0.0.1";

    /** 不填取 server.port */
    private Integer port;

    private String language = "Java 17";

    private String version = "1.0.0";

    private boolean registryEnabled = true;

    private long heartbeatInterval = 30000L;

    private Trace trace = new Trace();

    @Data
    public static class Trace {

        private boolean enabled = true;

        /** 单条报文体最大采集长度，超出截断 */
        private int maxBodySize = 8192;

        /** 采样率 0~1 */
        private double sampleRate = 1.0d;

        /** 批量上报条数 */
        private int batchSize = 50;

        /** 上报间隔（ms） */
        private long flushInterval = 3000L;

        /** 不采集的路径（前缀匹配） */
        private List<String> excludePaths = new ArrayList<>(List.of(
                "/actuator", "/error", "/favicon.ico", "/health"
        ));
    }
}
