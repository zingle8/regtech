package com.zimo.regtech.gateway.bridge;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

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
}
