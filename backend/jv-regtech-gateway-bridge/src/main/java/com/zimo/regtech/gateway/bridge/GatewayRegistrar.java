package com.zimo.regtech.gateway.bridge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 自动接入：启动时注册实例、运行期心跳、退出时下线。
 *
 * <p>对应 api-gateway 控制台「接入中心 → 自动接入」与「服务发现」。</p>
 */
@Slf4j
@Component
public class GatewayRegistrar implements ApplicationRunner, DisposableBean {

    private static final String PATH_REGISTER = "/discovery/services/register";
    private static final String PATH_HEARTBEAT = "/discovery/services/heartbeat";
    private static final String PATH_DEREGISTER = "/discovery/services/deregister";

    private final GatewayBridgeProperties properties;
    private final Environment environment;
    private final RestClient restClient;
    private final ScheduledExecutorService executor;

    public GatewayRegistrar(GatewayBridgeProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory())
                .build();
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "regtech-gateway-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!active()) {
            return;
        }
        register();
        long interval = Math.max(5000L, properties.getHeartbeatInterval());
        executor.scheduleWithFixedDelay(this::heartbeatQuietly, interval, interval, TimeUnit.MILLISECONDS);
    }

    public void register() {
        Payloads.ServiceInstance instance = buildInstance();
        try {
            restClient.post()
                    .uri(url(PATH_REGISTER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(instance)
                    .retrieve()
                    .toBodilessEntity();
            log.info("已接入 API 网关平台：{} ({}) {}:{}",
                    instance.getAppId(), instance.getServiceName(), instance.getIp(), instance.getPort());
        } catch (Exception e) {
            log.warn("接入 API 网关平台失败（不影响业务启动）：{}", e.getMessage());
        }
    }

    private void heartbeatQuietly() {
        if (!active()) {
            return;
        }
        try {
            restClient.post()
                    .uri(url(PATH_HEARTBEAT))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildInstance())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.debug("网关平台心跳失败：{}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
        if (!active()) {
            return;
        }
        try {
            restClient.method(HttpMethod.DELETE)
                    .uri(url(PATH_DEREGISTER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildInstance())
                    .retrieve()
                    .toBodilessEntity();
            log.info("已从 API 网关平台下线：{}", properties.getServiceName());
        } catch (Exception e) {
            log.debug("网关平台下线失败：{}", e.getMessage());
        }
    }

    private boolean active() {
        return properties.isEnabled() && properties.isRegistryEnabled();
    }

    private Payloads.ServiceInstance buildInstance() {
        Payloads.ServiceInstance instance = new Payloads.ServiceInstance();
        instance.setAppId(properties.getAppId());
        instance.setAppName(properties.getAppName());
        instance.setServiceName(properties.getServiceName());
        instance.setIp(properties.getIp());
        instance.setPort(resolvePort());
        instance.setLanguage(properties.getLanguage());
        instance.setVersion(properties.getVersion());
        instance.setRegisterTime(LocalDateTime.now());
        return instance;
    }

    private int resolvePort() {
        if (properties.getPort() != null) {
            return properties.getPort();
        }
        try {
            return Integer.parseInt(String.valueOf(environment.getProperty("server.port", "8080")));
        } catch (Exception e) {
            return 8080;
        }
    }

    private String url(String path) {
        String base = properties.getBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}
