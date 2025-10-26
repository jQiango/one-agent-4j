package com.all.in.one.agent.common.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * One Agent 配置属性
 *
 * @author One Agent 4J
 * @since 1.0.0
 */
@Data
public class AgentProperties {

    /**
     * 是否启用 (默认启用)
     */
    private boolean enabled = true;

    /**
     * 应用名称 (如果不配置则从 spring.application.name 获取)
     */
    private String appName;

    /**
     * 环境: dev/test/uat/prod (如果不配置则从 spring.profiles.active 获取)
     */
    private String environment;

    /**
     * 采样率 (0.0-1.0, 1.0 表示 100% 采样)
     */
    private double samplingRate = 1.0;

    /**
     * 上报服务器地址
     */
    private String serverUrl;

    /**
     * 连接超时时间 (毫秒)
     */
    private int connectTimeout = 3000;

    /**
     * 读取超时时间 (毫秒)
     */
    private int readTimeout = 5000;

    /**
     * 上报策略
     */
    private ReportStrategy reportStrategy = new ReportStrategy();

    /**
     * 异常捕获配置
     */
    private CaptureConfig captureConfig = new CaptureConfig();

    /**
     * 上报策略配置
     */
    @Data
    public static class ReportStrategy {
        /**
         * 上报模式: sync(同步)/async(异步)/batch(批量)
         */
        private String mode = "async";

        /**
         * 批量上报批次大小
         */
        private int batchSize = 10;

        /**
         * 批量上报最大等待时间 (毫秒)
         */
        private int maxWaitTime = 5000;

        /**
         * 异步上报队列大小
         */
        private int queueSize = 1000;

        /**
         * 异步上报线程池大小
         */
        private int threadPoolSize = 2;

        /**
         * 失败重试次数
         */
        private int retryTimes = 3;
    }

    /**
     * 异常捕获配置
     */
    @Data
    public static class CaptureConfig {
        /**
         * 是否启用 Filter 捕获
         */
        private boolean enableFilter = true;

        /**
         * 是否启用 ControllerAdvice 捕获
         */
        private boolean enableControllerAdvice = true;

        /**
         * 是否启用 AOP 捕获
         */
        private boolean enableAop = true;

        /**
         * AOP 切入点表达式
         */
        private String aopPointcut = "execution(* com.*.*.*.service..*.*(..))";

        /**
         * 是否捕获请求参数
         */
        private boolean captureRequestParams = true;

        /**
         * 是否捕获请求头
         */
        private boolean captureRequestHeaders = false;

        /**
         * 是否捕获请求 Body
         */
        private boolean captureRequestBody = false;

        /**
         * 请求 Body 最大长度 (字节)
         */
        private int maxBodyLength = 1024;

        /**
         * 堆栈最大深度
         */
        private int maxStackDepth = 50;

        /**
         * 忽略的异常类型
         */
        private List<String> ignoredExceptions = new ArrayList<>();

        /**
         * 忽略的包路径
         */
        private List<String> ignoredPackages = new ArrayList<>();
    }
}
