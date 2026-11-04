package com.zimo.regtech.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 鉴权白名单：每个服务只开放自己的匿名接口（登录、健康检查、OpenAI 兼容入口等）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "regtech.auth")
public class AuthProperties {

    /** 不需要登录即可访问的路径（Ant 风格，相对 context-path） */
    private List<String> excludePaths = new ArrayList<>(List.of(
            "/actuator/**", "/error", "/favicon.ico", "/health"
    ));
}
