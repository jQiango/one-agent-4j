package com.all.in.one.agent.starter.reporter;

import com.all.in.one.agent.common.config.AgentProperties;
import com.all.in.one.agent.common.model.ExceptionInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 异常上报器
 * <p>
 * 负责将异常信息上报到服务器
 * 支持三种模式: 同步/异步/批量
 * </p>
 *
 * @author One Agent 4J
 * @since 1.0.0
 */
@Slf4j
public class ExceptionReporter {

    private final AgentProperties properties;
    private final WebClient webClient;

    // 异步上报相关
    private final ExecutorService executorService;
    private final BlockingQueue<ExceptionInfo> asyncQueue;

    // 批量上报相关
    private final List<ExceptionInfo> batchBuffer;
    private final ScheduledExecutorService batchScheduler;

    public ExceptionReporter(AgentProperties properties) {
        this.properties = properties;

        // 初始化 WebClient
        this.webClient = WebClient.builder()
                .baseUrl(properties.getServerUrl() != null ? properties.getServerUrl() : "http://localhost:8080")
                .build();

        AgentProperties.ReportStrategy strategy = properties.getReportStrategy();

        // 初始化异步上报
        if ("async".equals(strategy.getMode())) {
            this.asyncQueue = new LinkedBlockingQueue<>(strategy.getQueueSize());
            this.executorService = new ThreadPoolExecutor(
                    strategy.getThreadPoolSize(),
                    strategy.getThreadPoolSize(),
                    60L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(100),
                    r -> {
                        Thread thread = new Thread(r, "one-agent-reporter");
                        thread.setDaemon(true);
                        return thread;
                    }
            );

            // 启动异步消费线程
            startAsyncConsumer();
            this.batchBuffer = null;
            this.batchScheduler = null;

        } else if ("batch".equals(strategy.getMode())) {
            // 初始化批量上报
            this.batchBuffer = new ArrayList<>(strategy.getBatchSize());
            this.batchScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "one-agent-batch-scheduler");
                thread.setDaemon(true);
                return thread;
            });

            // 启动定时批量上报
            startBatchScheduler();
            this.asyncQueue = null;
            this.executorService = null;

        } else {
            // 同步模式
            this.asyncQueue = null;
            this.executorService = null;
            this.batchBuffer = null;
            this.batchScheduler = null;
        }

        log.info("ExceptionReporter 初始化完成 - mode={}, serverUrl={}",
                strategy.getMode(),
                properties.getServerUrl());
    }

    /**
     * 上报异常
     *
     * @param exceptionInfo 异常信息
     */
    public void report(ExceptionInfo exceptionInfo) {
        String mode = properties.getReportStrategy().getMode();

        switch (mode) {
            case "sync" -> reportSync(exceptionInfo);
            case "async" -> reportAsync(exceptionInfo);
            case "batch" -> reportBatch(exceptionInfo);
            default -> {
                log.warn("未知的上报模式: {}, 使用同步模式", mode);
                reportSync(exceptionInfo);
            }
        }
    }

    /**
     * 同步上报
     */
    private void reportSync(ExceptionInfo exceptionInfo) {
        try {
            sendToServer(exceptionInfo)
                    .timeout(Duration.ofMillis(properties.getReadTimeout()))
                    .block();
            log.debug("同步上报成功 - fingerprint={}", exceptionInfo.getFingerprint());
        } catch (Exception e) {
            log.error("同步上报失败 - fingerprint={}, error={}",
                    exceptionInfo.getFingerprint(),
                    e.getMessage());
        }
    }

    /**
     * 异步上报
     */
    private void reportAsync(ExceptionInfo exceptionInfo) {
        boolean offered = asyncQueue.offer(exceptionInfo);
        if (!offered) {
            log.warn("异步队列已满，丢弃异常 - fingerprint={}", exceptionInfo.getFingerprint());
        }
    }

    /**
     * 批量上报
     */
    private synchronized void reportBatch(ExceptionInfo exceptionInfo) {
        batchBuffer.add(exceptionInfo);

        // 如果达到批次大小，立即上报
        if (batchBuffer.size() >= properties.getReportStrategy().getBatchSize()) {
            flushBatch();
        }
    }

    /**
     * 启动异步消费线程
     */
    private void startAsyncConsumer() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ExceptionInfo exceptionInfo = asyncQueue.take();
                    sendToServer(exceptionInfo).subscribe(
                            response -> log.debug("异步上报成功 - fingerprint={}",
                                    exceptionInfo.getFingerprint()),
                            error -> log.error("异步上报失败 - fingerprint={}, error={}",
                                    exceptionInfo.getFingerprint(),
                                    error.getMessage())
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("异步消费异常", e);
                }
            }
        });
    }

    /**
     * 启动批量上报定时任务
     */
    private void startBatchScheduler() {
        batchScheduler.scheduleAtFixedRate(
                this::flushBatch,
                properties.getReportStrategy().getMaxWaitTime(),
                properties.getReportStrategy().getMaxWaitTime(),
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 刷新批量缓冲区
     */
    private synchronized void flushBatch() {
        if (batchBuffer.isEmpty()) {
            return;
        }

        List<ExceptionInfo> toSend = new ArrayList<>(batchBuffer);
        batchBuffer.clear();

        try {
            sendBatchToServer(toSend).subscribe(
                    response -> log.info("批量上报成功 - count={}", toSend.size()),
                    error -> log.error("批量上报失败 - count={}, error={}",
                            toSend.size(),
                            error.getMessage())
            );
        } catch (Exception e) {
            log.error("批量上报异常 - count={}", toSend.size(), e);
        }
    }

    /**
     * 发送单条异常到服务器
     */
    private Mono<String> sendToServer(ExceptionInfo exceptionInfo) {
        if (properties.getServerUrl() == null) {
            log.debug("未配置 serverUrl，跳过上报 - fingerprint={}", exceptionInfo.getFingerprint());
            return Mono.just("skipped");
        }

        return webClient.post()
                .uri("/api/exceptions")
                .bodyValue(exceptionInfo)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.error("上报异常失败 - fingerprint={}, error={}",
                        exceptionInfo.getFingerprint(),
                        e.getMessage()));
    }

    /**
     * 批量发送异常到服务器
     */
    private Mono<String> sendBatchToServer(List<ExceptionInfo> exceptionInfos) {
        if (properties.getServerUrl() == null) {
            log.debug("未配置 serverUrl，跳过批量上报 - count={}", exceptionInfos.size());
            return Mono.just("skipped");
        }

        return webClient.post()
                .uri("/api/exceptions/batch")
                .bodyValue(exceptionInfos)
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 关闭上报器
     */
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
        if (batchScheduler != null) {
            flushBatch(); // 最后一次刷新
            batchScheduler.shutdown();
        }
        log.info("ExceptionReporter 已关闭");
    }
}
