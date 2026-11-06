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

    /** 服务ID（英文唯一标识），如 jv-regtech-kb；留空时回退使用 serviceName（兼容旧配置） */
    private String serviceId = "";

    /** 服务名（中文显示名），如 知识库服务；留空时服务端显示同 serviceId */
    private String serviceName = "regtech-service";

    private String ip = "127.0.0.1";

    /** 不填取 server.port */
    private Integer port;

    private String language = "Java 17";

    private String version = "1.0.0";

    private boolean registryEnabled = true;

    private long heartbeatInterval = 30000L;

    /**
     * 上报通道共享密钥（X-Gateway-Client-Token）：注册 / 心跳 / 链路采集在网关
     * skipPaths 白名单放行后会做二次校验，必须与网关 proxy 的
     * {@code gateway.auth.client-token}（默认 ${GATEWAY_CLIENT_TOKEN:dev-gateway-token}）同值。
     */
    private String clientToken = "dev-gateway-token";

    /** 入口鉴权（防绕过网关直连），协议与平台 starter 的 GatewayAuthFilter 一致，默认关闭 */
    private Auth auth = new Auth();

    private Trace trace = new Trace();

    /** 入口鉴权配置（默认关闭；启用后每个请求回调网关开放验签接口） */
    @Data
    public static class Auth {

        private boolean enabled = false;

        /** 验签地址；留空则用 baseUrl + /open/auth/token/verify */
        private String verifyUrl = "";

        /** 豁免路径前缀（健康检查等），命中直接放行 */
        private List<String> exemptPaths = new ArrayList<>(List.of("/health", "/actuator", "/error", "/favicon.ico"));

        /** 验签结果缓存秒数（同一 token 不重复回调网关） */
        private int cacheSeconds = 60;

        /** 网关不可达时 true 放行（可用性优先）/ false 拒绝（安全优先，默认） */
        private boolean failOpen = false;
    }

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
