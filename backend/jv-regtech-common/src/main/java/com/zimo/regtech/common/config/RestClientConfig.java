package com.zimo.regtech.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 服务间调用客户端。
 *
 * <p>固定 HTTP/1.1（避免 h2c 升级在部分代理上挂起），并显式设置超时：
 * 平台/模型不可达时应快速失败，不能拖死业务线程。</p>
 *
 * <p>使用 Spring Boot 自动装配的 {@link RestClient.Builder}，使网关桥接注册的
 * {@code RestClientCustomizer}（链路 traceparent 注入 + CLIENT span 采集）自动生效。</p>
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(120));
        return builder.requestFactory(factory).build();
    }
}
