package com.zimo.regtech.gateway.bridge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 调用日志上报器：内存队列 + 异步批量上报，队列满直接丢弃，绝不影响业务。
 */
@Slf4j
@Component
public class TraceReporter implements DisposableBean {

    private static final String PATH_TRACE_BATCH = "/trace/logs/batch";

    private final GatewayBridgeProperties properties;
    private final RestClient restClient;
    private final BlockingQueue<Payloads.TraceLog> queue = new ArrayBlockingQueue<>(1024);
    private final ScheduledExecutorService executor;
    private volatile boolean closed = false;

    public TraceReporter(GatewayBridgeProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "regtech-gateway-trace-reporter");
            thread.setDaemon(true);
            return thread;
        });
        long interval = Math.max(1000L, properties.getTrace().getFlushInterval());
        this.executor.scheduleWithFixedDelay(this::flushQuietly, interval, interval, TimeUnit.MILLISECONDS);
    }

    public void offer(Payloads.TraceLog log) {
        if (closed || log == null) {
            return;
        }
        // 满了直接丢，采集不能反压业务
        queue.offer(log);
    }

    public int flush() {
        int total = 0;
        int batchSize = Math.max(1, properties.getTrace().getBatchSize());
        while (!queue.isEmpty()) {
            List<Payloads.TraceLog> batch = new ArrayList<>(batchSize);
            queue.drainTo(batch, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            try {
                restClient.post()
                        .uri(url(PATH_TRACE_BATCH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(batch)
                        .retrieve()
                        .toBodilessEntity();
                total += batch.size();
            } catch (Exception e) {
                log.debug("上报网关调用日志失败，丢弃 {} 条：{}", batch.size(), e.getMessage());
                break;
            }
        }
        return total;
    }

    private void flushQuietly() {
        try {
            if (properties.isEnabled()) {
                flush();
            }
        } catch (Exception e) {
            log.debug("定时刷写调用日志异常：{}", e.getMessage());
        }
    }

    private String url(String path) {
        String base = properties.getBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }

    @Override
    public void destroy() {
        closed = true;
        executor.shutdownNow();
        flushQuietly();
    }
}
