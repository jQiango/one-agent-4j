package com.all.in.one.agent.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 异常信息模型
 *
 * @author One Agent 4J
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 环境: dev/test/uat/prod
     */
    private String environment;

    /**
     * 服务实例 ID
     */
    private String instanceId;

    /**
     * 主机名
     */
    private String hostname;

    /**
     * 服务 IP
     */
    private String ip;

    /**
     * 异常类型: NullPointerException, SQLException 等
     */
    private String exceptionType;

    /**
     * 异常消息
     */
    private String exceptionMessage;

    /**
     * 完整堆栈信息
     */
    private String stackTrace;

    /**
     * 异常指纹 (用于去重)
     */
    private String fingerprint;

    /**
     * 错误类名
     */
    private String errorClass;

    /**
     * 错误方法名
     */
    private String errorMethod;

    /**
     * 错误行号
     */
    private Integer errorLine;

    /**
     * 错误位置: com.example.UserService.getUser:123
     */
    private String errorLocation;

    /**
     * HTTP 请求相关信息
     */
    private RequestInfo requestInfo;

    /**
     * 线程信息
     */
    private ThreadInfo threadInfo;

    /**
     * 上下文信息 (自定义键值对)
     */
    private Map<String, Object> context;

    /**
     * 发生时间
     */
    private Instant occurredAt;

    /**
     * 上报时间
     */
    private Instant reportedAt;

    /**
     * TraceId (链路追踪)
     */
    private String traceId;

    /**
     * SpanId (链路追踪)
     */
    private String spanId;

    /**
     * HTTP 请求信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 请求方法: GET/POST/PUT/DELETE
         */
        private String method;

        /**
         * 请求 URI
         */
        private String uri;

        /**
         * 请求参数
         */
        private Map<String, String> params;

        /**
         * 请求头
         */
        private Map<String, String> headers;

        /**
         * 请求 Body (可能为空或太大被截断)
         */
        private String body;

        /**
         * 客户端 IP
         */
        private String clientIp;

        /**
         * User-Agent
         */
        private String userAgent;
    }

    /**
     * 线程信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreadInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 线程 ID
         */
        private Long threadId;

        /**
         * 线程名称
         */
        private String threadName;

        /**
         * 线程组名称
         */
        private String threadGroupName;

        /**
         * 线程优先级
         */
        private Integer priority;

        /**
         * 是否守护线程
         */
        private Boolean daemon;
    }
}
