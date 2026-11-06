package com.zimo.regtech.gateway.bridge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;

/**
 * 服务间调用链路拦截器（RestClient / RestTemplate 通用），与 api-gateway 平台 starter 的
 * TracePropagationInterceptor 同语义：
 *
 * <ul>
 *   <li>从 {@link TraceContext} 取当前链路上文，生成 CLIENT span 并异步上报；</li>
 *   <li>向下游注入 traceparent（W3C）+ X-Gateway-Trace-Id（兼容旧 SDK），下游服务的
 *       {@link GatewayTraceFilter} 读取并作为自己 SERVER span 的 parent，链路就此串联；</li>
 *   <li>没有链路上文（如定时任务直接发起的调用）时以本次调用为新链路根。</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class TracePropagationInterceptor implements ClientHttpRequestInterceptor {

    private static final String HEADER_TRACE_ID = "X-Gateway-Trace-Id";
    private static final String HEADER_TRACEPARENT = "traceparent";

    private final GatewayBridgeProperties properties;
    private final TraceReporter reporter;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        TraceContext.Ctx ctx = TraceContext.get();
        String clientSpanId = TraceContextUtil.newSpanId();

        // 向下游传播：有上文则延续链路；没有则以本次调用为新链路根
        String traceId = ctx != null ? ctx.traceId() : TraceContextUtil.newTraceId();
        request.getHeaders().set(HEADER_TRACEPARENT, TraceContextUtil.format(traceId, clientSpanId));
        request.getHeaders().set(HEADER_TRACE_ID, traceId);

        long start = System.currentTimeMillis();
        LocalDateTime startTime = LocalDateTime.now();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            // 网关会在响应头回写自己的服务名，拓扑图的节点由此与服务发现对齐
            String peer = response.getHeaders().getFirst("X-Gateway-Served-By");
            reportClientSpan(request.getURI(), request.getMethod().name(), traceId,
                    ctx == null ? null : ctx.spanId(), clientSpanId,
                    response.getStatusCode().value(), start, startTime, peer);
            return response;
        } catch (IOException e) {
            reportClientSpan(request.getURI(), request.getMethod().name(), traceId,
                    ctx == null ? null : ctx.spanId(), clientSpanId,
                    502, start, startTime, null);
            throw e;
        }
    }

    /** CLIENT span：peerService 优先取网关回写的服务名，否则从目标地址提取（host:port） */
    private void reportClientSpan(URI uri, String method, String traceId,
                                  String parentSpanId, String spanId,
                                  int statusCode, long start, LocalDateTime startTime, String peerHeader) {
        try {
            Payloads.SpanRecord span = new Payloads.SpanRecord();
            span.setTraceId(traceId);
            span.setSpanId(spanId);
            span.setParentSpanId(parentSpanId);
            span.setKind(Payloads.SpanRecord.KIND_CLIENT);
            span.setAppId(properties.getAppId());
            span.setAppName(properties.getAppName());
            span.setServiceId(properties.effectiveServiceId());
            span.setServiceName(properties.effectiveServiceId());
            span.setApi(method + " " + uri.getRawPath());
            if (peerHeader != null && !peerHeader.isEmpty()) {
                span.setPeerService(peerHeader);
            } else {
                int port = uri.getPort() > 0 ? uri.getPort()
                        : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
                span.setPeerService(uri.getHost() + ":" + port);
            }
            span.setStatusCode(statusCode);
            span.setErrType(ErrTypes.resolve(statusCode));
            span.setCost((int) (System.currentTimeMillis() - start));
            span.setStartTime(startTime);
            reporter.offerSpan(span);
        } catch (Exception e) {
            log.debug("记录 CLIENT span 失败：{}", e.getMessage());
        }
    }
}
