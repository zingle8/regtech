package com.zimo.regtech.common.web;

import com.zimo.regtech.common.result.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 探活接口：网关与服务编排脚本使用，不需要登录。
 */
@RestController
public class HealthController {

    @Value("${spring.application.name:regtech-service}")
    private String applicationName;

    @Value("${server.port:0}")
    private int port;

    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", applicationName);
        data.put("port", port);
        data.put("status", "UP");
        data.put("time", LocalDateTime.now());
        return R.ok(data);
    }
}
