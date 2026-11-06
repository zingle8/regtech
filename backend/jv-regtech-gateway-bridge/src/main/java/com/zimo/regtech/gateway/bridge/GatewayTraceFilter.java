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

/**
 * 调用日志 + 链路 span 采集：与 api-gateway 平台 starter 的 GatewayTraceFilter 行为一致。
 *
 * <ul>
 *   <li>请求日志走 {@code /trace/logs/batch}，SERVER span 走 {@code /trace/spans/batch}</li>
 *   <li>链路上文写入 {@link TraceContext}，出站调用由 {@link TracePropagationInterceptor}
 *       生成 CLIENT span 并向下游注入 traceparent</li>
 *   <li>链路 ID 兼容 W3C {@code traceparent} 与旧版 {@code X-Gateway-Trace-Id}</li>
 *   <li>只在内存中旁路复制报文体，不重写响应；SSE 等流式响应只记元数据</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class GatewayTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Gateway-Trace-Id";
    private static final String HEADER_TRACEPARENT = "traceparent";

    private final GatewayBridgeProperties properties;
    private final TraceReporter reporter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!shouldTrace(request)) {
            chain.doFilter(request, response);
            return;
        }

        // 链路上文：优先 traceparent（W3C），兜底旧 X-Gateway-Trace-Id，都没有则本服务是链路根
        TraceContextUtil.Parsed upstream = TraceContextUtil.parse(request.getHeader(HEADER_TRACEPARENT));
        if (upstream == null) {
            upstream = TraceContextUtil.fromLegacy(request.getHeader(TRACE_ID_HEADER));
        }
        String traceId = upstream != null ? upstream.traceId() : TraceContextUtil.newTraceId();
        String serverSpanId = TraceContextUtil.newSpanId();
        TraceContext.set(traceId, serverSpanId);

        // SSE / 流式响应不能包装：ContentCachingResponseWrapper 会在异步完成前提交响应，
        // 导致前端收到空 body。流式接口只传播链路（供出站 CLIENT span 使用），不采集日志与 span。
        if (isStreaming(request)) {
            try {
                chain.doFilter(request, response);
            } finally {
                TraceContext.clear();
            }
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
                Payloads.TraceLog traceLog = buildLog(request, requestWrapper, responseWrapper, reqTime, cost, traceId);
                Payloads.SpanRecord span = buildSpan(request, responseWrapper, traceId,
                        serverSpanId, upstream == null ? null : upstream.spanId(), reqTime, cost);
                if (request.isAsyncStarted()) {
                    // 异步请求由容器后续派发，此时不能回写 body
                    reporter.offer(traceLog);
                    reporter.offerSpan(span);
                } else {
                    responseWrapper.copyBodyToResponse();
                    reporter.offer(traceLog);
                    reporter.offerSpan(span);
                }
            } catch (Exception e) {
                log.debug("采集调用日志失败：{}", e.getMessage());
            } finally {
                TraceContext.clear();
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
                                       long cost,
                                       String traceId) {
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
        log.setTraceId(traceId);
        log.setProtocol(request.getProtocol());
        log.setReqFormat(request.getContentType());
        log.setResFormat(resContentType);
        log.setReqIp(resolveIp(request));
        log.setReqPort(request.getRemotePort());
        log.setResIp(properties.getIp());
        log.setResPort(properties.getPort());
        log.setStatusCode(responseWrapper.getStatus());
        log.setErrType(ErrTypes.resolve(responseWrapper.getStatus()));
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

    /** SERVER span：父 span 为网关 / 上游透传的 client span，形成完整链路 */
    private Payloads.SpanRecord buildSpan(HttpServletRequest request,
                                          ContentCachingResponseWrapper responseWrapper,
                                          String traceId,
                                          String serverSpanId,
                                          String parentSpanId,
                                          LocalDateTime startTime,
                                          long cost) {
        Payloads.SpanRecord span = new Payloads.SpanRecord();
        span.setTraceId(traceId);
        span.setSpanId(serverSpanId);
        span.setParentSpanId(parentSpanId == null ? "" : parentSpanId);
        span.setKind(Payloads.SpanRecord.KIND_SERVER);
        span.setAppId(properties.getAppId());
        span.setAppName(properties.getAppName());
        span.setServiceName(properties.getServiceName());
        span.setApi(request.getMethod() + " " + request.getRequestURI());
        span.setPeerService("");
        span.setStatusCode(responseWrapper.getStatus());
        span.setErrType(ErrTypes.resolve(responseWrapper.getStatus()));
        span.setCost((int) cost);
        span.setStartTime(startTime);
        return span;
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

    private static String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
