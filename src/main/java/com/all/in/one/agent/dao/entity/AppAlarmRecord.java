package com.all.in.one.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警记录实体
 * <p>
 * 存储系统捕获的所有告警信息，包括异常详情、请求上下文、AI去噪结果等
 * 对应数据库表: app_alarm_record
 * </p>
 *
 * @author One Agent 4J
 * @since 1.0.0
 */
@Data
@TableName("app_alarm_record")
public class AppAlarmRecord {

    // ==================== 主键 ====================

    /**
     * 主键ID
     * 自增长，唯一标识一条告警记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    // ==================== 应用信息 ====================

    /**
     * 应用名称
     * 标识产生告警的应用系统，如: user-service, order-service
     * 必填字段，用于区分不同的微服务
     */
    private String appName;

    /**
     * 运行环境
     * 标识应用运行的环境，取值: dev/test/uat/prod
     * 必填字段，不同环境的告警处理策略不同
     */
    private String environment;

    /**
     * 实例ID
     * 标识具体的服务实例，通常为容器ID或进程ID
     * 可选字段，用于定位问题发生的具体实例
     */
    private String instanceId;

    /**
     * 主机名
     * 服务运行的主机名或容器名
     * 可选字段，辅助问题定位
     */
    private String hostname;

    /**
     * IP地址
     * 服务实例的IP地址
     * 可选字段，用于网络问题排查
     */
    private String ip;

    // ==================== 异常信息 ====================

    /**
     * 异常类型
     * Java异常的完整类名，如: java.lang.NullPointerException
     * 必填字段，用于异常分类和统计
     */
    private String exceptionType;

    /**
     * 异常消息
     * 异常的详细描述信息，即 exception.getMessage()
     * 可选字段，提供异常的具体原因
     */
    private String exceptionMessage;

    /**
     * 堆栈跟踪
     * 完整的异常堆栈信息，用于问题诊断
     * 可选字段，存储为文本类型以支持长堆栈
     */
    private String stackTrace;

    /**
     * 异常指纹
     * 由异常类型、错误位置、消息关键部分计算的MD5值
     * 必填字段，用于去重和聚合相同异常
     * 计算规则: MD5(exceptionType + errorLocation + messageKeyPart)
     */
    private String fingerprint;

    // ==================== 错误位置 ====================

    /**
     * 错误类名
     * 异常发生所在的Java类的完整类名
     * 可选字段，从堆栈第一行提取
     * 示例: com.example.service.UserService
     */
    private String errorClass;

    /**
     * 错误方法名
     * 异常发生所在的方法名
     * 可选字段，从堆栈第一行提取
     * 示例: getUserById
     */
    private String errorMethod;

    /**
     * 错误行号
     * 异常发生的代码行号
     * 可选字段，从堆栈第一行提取
     * 用于精确定位问题代码
     */
    private Integer errorLine;

    /**
     * 错误位置
     * 错误位置的完整描述，格式: 类名.方法名:行号
     * 可选字段，综合展示错误位置
     * 示例: com.example.service.UserService.getUserById:123
     */
    private String errorLocation;

    // ==================== 请求信息 ====================

    /**
     * 请求方法
     * HTTP请求方法，如: GET/POST/PUT/DELETE
     * 可选字段，仅HTTP请求产生的异常才有此信息
     */
    private String requestMethod;

    /**
     * 请求URI
     * HTTP请求的URI路径
     * 可选字段，用于关联异常与具体的API接口
     * 示例: /api/users/123
     */
    private String requestUri;

    /**
     * 请求参数
     * HTTP请求的参数，JSON格式存储
     * 可选字段，包含查询参数和表单参数
     * 敏感信息（如密码）会被脱敏
     */
    private String requestParams;

    /**
     * 客户端IP
     * 发起请求的客户端IP地址
     * 可选字段，用于分析问题来源
     * 支持IPv4和IPv6格式
     */
    private String clientIp;

    // ==================== 线程信息 ====================

    /**
     * 线程ID
     * Java线程的ID，即 Thread.getId()
     * 可选字段，用于多线程问题排查
     */
    private Long threadId;

    /**
     * 线程名称
     * Java线程的名称，即 Thread.getName()
     * 可选字段，辅助定位并发问题
     * 示例: http-nio-8080-exec-1
     */
    private String threadName;

    // ==================== 链路追踪 ====================

    /**
     * 链路追踪ID
     * 分布式追踪系统的TraceId，用于关联整个请求链路
     * 可选字段，支持SkyWalking、Zipkin等追踪系统
     * 用于跨服务问题排查
     */
    private String traceId;

    /**
     * Span ID
     * 当前服务在链路中的SpanId
     * 可选字段，标识当前服务的调用节点
     */
    private String spanId;

    // ==================== 时间信息 ====================

    /**
     * 异常发生时间
     * 异常实际发生的精确时间点
     * 必填字段，使用应用服务器的本地时间
     * 用于时序分析和问题回溯
     */
    private LocalDateTime occurredAt;

    /**
     * 上报时间
     * 异常信息上报到监控系统的时间
     * 必填字段，通常略晚于occurredAt
     * 用于计算上报延迟
     */
    private LocalDateTime reportedAt;

    // ==================== AI 去噪相关 ====================

    /**
     * AI是否已处理
     * 标识该异常是否经过AI去噪分析
     * 默认值: false
     * true: 已经过AI分析，有对应的决策结果
     * false: 未经过AI分析或AI功能未启用
     */
    private Boolean aiProcessed;

    /**
     * AI决策结果
     * AI判断该异常是否需要告警的结果
     * 可选字段，取值: ALERT(需要告警) / IGNORE(忽略)
     * 仅当aiProcessed=true时有值
     */
    private String aiDecision;

    /**
     * AI决策原因
     * AI做出判断的详细理由说明
     * 可选字段，存储AI的分析结果和建议
     * 帮助运维人员理解AI的决策依据
     * 示例: "与历史异常#1001高度相似，判定为重复告警"
     */
    private String aiReason;

    // ==================== 审计字段 ====================

    /**
     * 创建时间
     * 记录插入数据库的时间
     * 必填字段，数据库自动设置
     * 默认值: CURRENT_TIMESTAMP
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     * 记录最后一次更新的时间
     * 必填字段，数据库自动更新
     * 默认值: CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
     */
    private LocalDateTime updatedAt;
}
