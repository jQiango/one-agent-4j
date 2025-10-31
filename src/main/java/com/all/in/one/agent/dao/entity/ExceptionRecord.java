package com.all.in.one.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异常记录实体
 *
 * @author One Agent 4J
 */
@Data
@TableName("exception_record")
public class ExceptionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 应用信息
    private String appName;
    private String environment;
    private String instanceId;
    private String hostname;
    private String ip;

    // 异常信息
    private String exceptionType;
    private String exceptionMessage;
    private String stackTrace;
    private String fingerprint;

    // 错误位置
    private String errorClass;
    private String errorMethod;
    private Integer errorLine;
    private String errorLocation;

    // 请求信息
    private String requestMethod;
    private String requestUri;
    private String requestParams;
    private String clientIp;

    // 线程信息
    private Long threadId;
    private String threadName;

    // 链路追踪
    private String traceId;
    private String spanId;

    // 时间信息
    private LocalDateTime occurredAt;
    private LocalDateTime reportedAt;

    // AI 去噪相关
    private Boolean aiProcessed;
    private String aiDecision;
    private String aiReason;

    // 审计字段
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
