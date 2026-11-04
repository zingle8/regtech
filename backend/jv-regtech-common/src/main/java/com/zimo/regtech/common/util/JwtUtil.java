package com.zimo.regtech.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：所有微服务共用同一密钥，任何服务都能独立校验令牌（无需回查用户库）。
 */
@Component
public class JwtUtil {

    @Value("${regtech.jwt.secret:ai-ncs-regtech-jwt-secret-key-change-me-please}")
    private String secret;

    @Value("${regtech.jwt.expire-hours:8}")
    private long expireHours;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发令牌。
     *
     * @param tokenVersion 令牌版本，退出登录时自增，使旧令牌立即失效
     */
    public String createToken(Long userId, String username, Integer tokenVersion) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("tv", tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(now)
                .expiration(expire)
                .signWith(key())
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        Object value = claims.get("userId");
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    public Integer getTokenVersion(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        Object value = claims.get("tv");
        return value == null ? 0 : Integer.valueOf(String.valueOf(value));
    }
}
