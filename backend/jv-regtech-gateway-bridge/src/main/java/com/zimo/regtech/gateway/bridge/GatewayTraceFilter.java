package com.zimo.regtech.gateway.bridge;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

/**
 * 调用日志采集：与 ai-gateway 的 GatewayTraceFilter 行为一致。
 *
 * <p>只在内存中旁路复制报文体，不重写响应；SSE 等流式响应只记元数据。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class GatewayTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Gateway-Trace-Id";

    private final GatewayBridgeProperties properties;
    private final TraceReporter reporter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // SSE / 流式响应不能包装：ContentCachingResponseWrapper 会在异步完成前提交响应，
        // 导致前端收到空 body。流式接口只记元数据，直接放行。
        if (!shouldTrace(request) || isStreaming(request)) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        LocalDateTime reqTime = LocalDateTime.now();
        long start = System.currentTimeMillis();

        try {
            chain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long cost = System.currentTimeMillis() - start;
            try {
                if (request.isAsyncStarted()) {
                    // 异步请求由容器后续派发，此时不能回写 body
                    reporter.offer(buildLog(request, requestWrapper, responseWrapper, reqTime, cost));
                } else {
                    responseWrapper.copyBodyToResponse();
                    reporter.offer(buildLog(request, requestWrapper, responseWrapper, reqTime, cost));
                }
            } catch (Exception e) {
                log.debug("采集调用日志失败：{}", e.getMessage());
            }
        }
    }

    private static boolean isStreaming(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && (uri.endsWith("/stream")
                || (uri.contains("/v1/chat/completions") && uri.contains("/ai/")));
    }

    private boolean shouldTrace(HttpServletRequest request) {
        if (!properties.isEnabled() || !properties.getTrace().isEnabled()) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        for (String exclude : properties.getTrace().getExcludePaths()) {
            if (uri.startsWith(exclude)) {
                return false;
            }
        }
        double rate = properties.getTrace().getSampleRate();
        return rate >= 1.0d || Math.random() < rate;
    }

    private Payloads.TraceLog buildLog(HttpServletRequest request,
                                       ContentCachingRequestWrapper requestWrapper,
                                       ContentCachingResponseWrapper responseWrapper,
                                       LocalDateTime reqTime,
                                       long cost) {
        int max = Math.max(256, properties.getTrace().getMaxBodySize());
        byte[] reqBytes = requestWrapper.getContentAsByteArray();
        byte[] resBytes = responseWrapper.getContentAsByteArray();
        String resContentType = responseWrapper.getContentType();

        Payloads.TraceLog log = new Payloads.TraceLog();
        log.setAppId(properties.getAppId());
        log.setAppName(properties.getAppName());
        log.setServiceName(properties.getServiceName());
        log.setApi(request.getMethod() + " " + request.getRequestURI());
        log.setReqParams(request.getQueryString());
        log.setTraceId(resolveTraceId(request));
        log.setProtocol(request.getProtocol());
        log.setReqFormat(request.getContentType());
        log.setResFormat(resContentType);
        log.setReqIp(resolveIp(request));
        log.setReqPort(request.getRemotePort());
        log.setResIp(properties.getIp());
        log.setResPort(properties.getPort());
        log.setStatusCode(responseWrapper.getStatus());
        log.setErrType(errType(responseWrapper.getStatus()));
        log.setCost((int) cost);
        log.setReqTime(reqTime);
        log.setResTime(LocalDateTime.now());
        log.setReqHeader(headers(request));
        log.setResHeader(headers(responseWrapper));
        log.setReqBytes((long) reqBytes.length);
        log.setResBytes((long) resBytes.length);

        boolean streaming = resContentType != null && resContentType.contains("text/event-stream");
        if (!streaming) {
            log.setReqBody(truncate(reqBytes, max));
            log.setResBody(truncate(resBytes, max));
        }
        return log;
    }

    private static String truncate(byte[] bytes, int max) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return text.length() > max ? text.substring(0, max) + "...[truncated]" : text;
    }

    private static String headers(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        for (String name : Collections.list(request.getHeaderNames())) {
            if ("authorization".equalsIgnoreCase(name) || "cookie".equalsIgnoreCase(name)) {
                sb.append(name).append(": ******\n");
                continue;
            }
            sb.append(name).append(": ").append(request.getHeader(name)).append('\n');
        }
        return sb.toString();
    }

    private static String headers(HttpServletResponse response) {
        StringBuilder sb = new StringBuilder();
        for (String name : response.getHeaderNames()) {
            sb.append(name).append(": ").append(response.getHeader(name)).append('\n');
        }
        return sb.toString();
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        return traceId == null || traceId.isEmpty()
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                : traceId;
    }

    private static String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String errType(int status) {
        if (status == 401 || status == 403) {
            return "鉴权失败";
        }
        if (status == 404) {
            return "路由失败";
        }
        if (status == 429) {
            return "限流拒绝";
        }
        if (status == 504) {
            return "上游响应超时";
        }
        return status >= 500 ? "上游响应异常" : null;
    }
}
