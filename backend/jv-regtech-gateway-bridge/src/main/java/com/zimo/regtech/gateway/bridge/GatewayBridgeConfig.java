package com.zimo.regtech.gateway.bridge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestClient;

/**
 * 采集过滤器注册：放在鉴权拦截器之前，保证 401 也能被记录。
 */
@Configuration
public class GatewayBridgeConfig {

    @Bean
    public FilterRegistrationBean<GatewayTraceFilter> gatewayTraceFilterRegistration(
            GatewayBridgeProperties properties, TraceReporter reporter) {
        FilterRegistrationBean<GatewayTraceFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new GatewayTraceFilter(properties, reporter));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("gatewayTraceFilter");
        return registration;
    }

    /**
     * 入口鉴权（默认关闭）：配置 regtech.gateway.auth.enabled=true 后生效。
     * 放在采集过滤器之后，保证 401 也能被采集记录。
     */
    @Bean
    public FilterRegistrationBean<GatewayAuthFilter> gatewayAuthFilterRegistration(
            GatewayBridgeProperties properties) {
        FilterRegistrationBean<GatewayAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setEnabled(properties.getAuth().isEnabled());
        registration.setFilter(new GatewayAuthFilter(properties));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setName("gatewayAuthFilter");
        return registration;
    }

    /**
     * 链路传播：通过 Spring Boot 自动装配的 RestClient.Builder 创建的客户端自动获得
     * CLIENT span 采集与 traceparent 注入（与平台 starter 的 tracePropagationCustomizer 一致）。
     */
    @Bean
    @ConditionalOnClass(RestClient.class)
    public RestClientCustomizer tracePropagationCustomizer(GatewayBridgeProperties properties,
                                                          TraceReporter reporter) {
        TracePropagationInterceptor interceptor = new TracePropagationInterceptor(properties, reporter);
        return builder -> builder.requestInterceptor(interceptor);
    }
}
