package com.zimo.regtech.common.security;

import com.zimo.regtech.common.context.UserContext;
import com.zimo.regtech.common.exception.BizException;
import com.zimo.regtech.common.result.ResultCode;
import com.zimo.regtech.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：校验 Authorization: Bearer &lt;token&gt;。
 *
 * <p>只校验签名与过期时间，不查库，因此任何微服务都能独立完成鉴权；
 * 账号禁用、令牌版本等强校验由 jv-regtech-user 在登录态接口中完成。</p>
 */
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        String token = header.substring(BEARER.length());
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }

        Object userId = claims.get("userId");
        UserContext.set(userId == null ? null : Long.valueOf(String.valueOf(userId)), claims.getSubject());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
