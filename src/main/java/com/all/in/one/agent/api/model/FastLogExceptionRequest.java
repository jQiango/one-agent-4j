package com.all.in.one.agent.api.model;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * FAST 日志平台异常上报请求
 * <p>
 * FAST 平台发现异常后，通过此接口上报到 One Agent 进行去噪和工单生成
 * </p>
 *
 * @author One Agent 4J
 */
@Data
public class FastLogExceptionRequest {

    /**
     * 应用名称（必填）
     */
    private String appName;

    /**
     * 环境: dev/test/uat/prod（必填）
     */
    private String environment;

    /**
     * 异常类型（必填）
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
     * 错误位置（类名.方法名:行号）
     */
    private String errorLocation;

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
     * 实例 ID
     */
    private String instanceId;

    /**
     * 主机名
     */
    private String hostname;

    /**
     * IP 地址
     */
    private String ip;

    /**
     * 请求方法 (GET/POST/etc)
     */
    private String requestMethod;

    /**
     * 请求 URI
     */
    private String requestUri;

    /**
     * 请求参数（JSON 字符串）
     */
    private String requestParams;

    /**
     * 客户端 IP
     */
    private String clientIp;

    /**
     * 线程 ID
     */
    private Long threadId;

    /**
     * 线程名称
     */
    private String threadName;

    /**
     * TraceId
     */
    private String traceId;

    /**
     * SpanId
     */
    private String spanId;

    /**
     * 发生时间（Unix 时间戳，毫秒）
     */
    private Long occurredAt;

    /**
     * 扩展字段
     */
    private Map<String, String> extra;
}
