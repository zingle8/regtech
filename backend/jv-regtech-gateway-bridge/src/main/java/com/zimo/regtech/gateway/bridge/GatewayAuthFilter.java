package com.zimo.regtech.gateway.bridge;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 入口鉴权：回调网关开放验签接口，防止绕过网关直连业务服务。
 *
 * <p>与 api-gateway 平台 starter 的 GatewayAuthFilter 同语义：</p>
 * <ul>
 *   <li>凭证：{@code Authorization: Bearer <jwt>} 或 {@code X-Api-Key <gwak-*>}，两者同一验签接口</li>
 *   <li>验签结果按 token 缓存（默认 60s），避免每个请求都回调网关</li>
 *   <li>{@code fail-open=false}（默认）时网关不可达直接拒绝，可用性优先可配 true</li>
 *   <li>默认关闭；各服务 yml 里 {@code regtech.gateway.auth.enabled: true} 启用</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class GatewayAuthFilter extends OncePerRequestFilter {

    private static final String PATH_TOKEN_VERIFY = "/open/auth/token/verify";

    private final GatewayBridgeProperties properties;
    private final Map<String, Long> cache = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        GatewayBridgeProperties.Auth auth = properties.getAuth();
        String path = request.getRequestURI();

        for (String exempt : auth.getExemptPaths()) {
            if (path.startsWith(exempt)) {
                chain.doFilter(request, response);
                return;
            }
        }

        String token = extractToken(request);
        if (token.isEmpty()) {
            deny(response, 401, "未提供访问凭证（Authorization: Bearer 或 X-Api-Key）");
            return;
        }

        Long expiresAt = cache.get(token);
        if (expiresAt != null && expiresAt > System.currentTimeMillis()) {
            chain.doFilter(request, response);
            return;
        }

        Boolean valid = verifyQuietly(token);
        if (valid == null) {
            // 网关不可达
            if (auth.isFailOpen()) {
                chain.doFilter(request, response);
            } else {
                deny(response, 403, "验签服务不可达");
            }
            return;
        }
        if (!valid) {
            deny(response, 401, "凭证无效或已过期");
            return;
        }

        if (cache.size() >= 2048) {
            cache.clear();
        }
        cache.put(token, System.currentTimeMillis() + auth.getCacheSeconds() * 1000L);
        chain.doFilter(request, response);
    }

    private static String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        String apiKey = request.getHeader("X-Api-Key");
        return apiKey == null ? "" : apiKey.trim();
    }

    /** 返回 null = 网关不可达；true/false = 验签结果 */
    private Boolean verifyQuietly(String token) {
        try {
            String url = verifyUrl();
            Map<String, Object> body = Map.of("token", token);
            org.springframework.http.client.JdkClientHttpRequestFactory factory =
                    new org.springframework.http.client.JdkClientHttpRequestFactory(
                            java.net.http.HttpClient.newBuilder()
                                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                                    .connectTimeout(java.time.Duration.ofSeconds(3))
                                    .build());
            factory.setReadTimeout(java.time.Duration.ofSeconds(3));
            RestClient.RequestBodySpec spec = RestClient.builder().requestFactory(factory).build()
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON);
            String clientToken = properties.getClientToken();
            if (clientToken != null && !clientToken.isBlank()) {
                spec = spec.header("X-Gateway-Client-Token", clientToken);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = spec.body(body).retrieve().body(Map.class);
            Object data = result == null ? null : result.get("data");
            return data instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("valid"));
        } catch (Exception e) {
            log.debug("网关验签回调失败：{}", e.getMessage());
            return null;
        }
    }

    private String verifyUrl() {
        String configured = properties.getAuth().getVerifyUrl();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String base = properties.getBaseUrl();
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return base + PATH_TOKEN_VERIFY;
    }

    private static void deny(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
