-- ================================================
-- One Agent 4J 数据库初始化脚本 (完整版)
-- ================================================
-- 版本: 2.0.0
-- 更新时间: 2025-11-07
-- 说明: 基于最新 POJO 对象生成,完全匹配 Java 实体类字段
--       - AppAlarmRecord.java (26 字段)
--       - AppAlarmTicket.java (34 字段)
-- ================================================

-- 删除已存在的数据库 (谨慎使用!)
-- DROP DATABASE IF EXISTS one_agent;

-- 创建数据库
CREATE DATABASE IF NOT EXISTS one_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE one_agent;

-- ================================================
-- 1. 告警记录表
-- ================================================
DROP TABLE IF EXISTS app_alarm_record;

CREATE TABLE app_alarm_record (
    -- 主键
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    -- 应用信息
    app_name VARCHAR(128) NOT NULL COMMENT '应用名称',
    environment VARCHAR(32) NOT NULL COMMENT '环境: dev/test/uat/prod',
    instance_id VARCHAR(128) COMMENT '实例ID',
    hostname VARCHAR(128) COMMENT '主机名',
    ip VARCHAR(64) COMMENT 'IP地址',

    -- 异常信息
    exception_type VARCHAR(255) NOT NULL COMMENT '异常类型',
    exception_message TEXT COMMENT '异常消息',
    stack_trace TEXT COMMENT '完整堆栈',
    fingerprint VARCHAR(64) NOT NULL COMMENT '异常指纹(MD5)',

    -- 错误位置
    error_class VARCHAR(255) COMMENT '错误类名',
    error_method VARCHAR(255) COMMENT '错误方法名',
    error_line INT COMMENT '错误行号',
    error_location VARCHAR(512) COMMENT '错误位置: 类名.方法名:行号',

    -- 请求信息
    request_method VARCHAR(16) COMMENT 'HTTP方法',
    request_uri VARCHAR(512) COMMENT '请求URI',
    request_params TEXT COMMENT '请求参数(JSON)',
    client_ip VARCHAR(64) COMMENT '客户端IP',

    -- 线程信息
    thread_id BIGINT COMMENT '线程ID',
    thread_name VARCHAR(255) COMMENT '线程名称',

    -- 链路追踪
    trace_id VARCHAR(64) COMMENT 'TraceId',
    span_id VARCHAR(64) COMMENT 'SpanId',

    -- 时间信息
    occurred_at DATETIME NOT NULL COMMENT '发生时间',
    reported_at DATETIME NOT NULL COMMENT '上报时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- AI 去噪相关
    ai_processed BOOLEAN DEFAULT FALSE COMMENT 'AI是否已处理',
    ai_decision VARCHAR(32) COMMENT 'AI决策结果: ALERT/IGNORE',
    ai_reason TEXT COMMENT 'AI决策原因',

    -- 索引
    INDEX idx_app_env (app_name, environment),
    INDEX idx_fingerprint (fingerprint),
    INDEX idx_exception_type (exception_type),
    INDEX idx_occurred_at (occurred_at),
    INDEX idx_created_at (created_at),
    INDEX idx_ai_processed (ai_processed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警记录表';

-- ================================================
-- 2. 告警工单表
-- ================================================
DROP TABLE IF EXISTS app_alarm_ticket;

CREATE TABLE app_alarm_ticket (
    -- 主键
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单ID',
    ticket_no VARCHAR(64) UNIQUE NOT NULL COMMENT '工单编号',

    -- 关联异常
    exception_record_id BIGINT NOT NULL COMMENT '关联的异常记录ID',
    exception_fingerprint VARCHAR(64) NOT NULL COMMENT '异常指纹',

    -- 服务信息
    service_name VARCHAR(128) NOT NULL COMMENT '服务名称',
    environment VARCHAR(32) NOT NULL COMMENT '环境',

    -- 问题信息
    title VARCHAR(255) NOT NULL COMMENT '工单标题',
    problem_type VARCHAR(64) NOT NULL COMMENT '问题类型',
    problem_category VARCHAR(32) NOT NULL COMMENT '问题分类',
    severity VARCHAR(16) NOT NULL COMMENT '严重级别: P0/P1/P2/P3/P4',

    -- 异常内容
    exception_type VARCHAR(255) NOT NULL COMMENT '异常类型',
    exception_message TEXT COMMENT '异常消息',
    stack_trace TEXT COMMENT '堆栈信息',
    error_location VARCHAR(512) COMMENT '错误位置',
    occurrence_count INT DEFAULT 1 COMMENT '发生次数',
    first_occurred_at DATETIME NOT NULL COMMENT '首次发生时间',
    last_occurred_at DATETIME NOT NULL COMMENT '最后发生时间',

    -- 责任人
    service_owner VARCHAR(64) COMMENT '项目负责人',
    assignee VARCHAR(64) COMMENT '处理人',
    reporter VARCHAR(64) DEFAULT 'AI-Agent' COMMENT '报告人',

    -- 处理状态
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/ASSIGNED/IN_PROGRESS/RESOLVED/CLOSED',
    progress INT DEFAULT 0 COMMENT '处理进度 0-100',

    -- 处理时间
    assigned_at DATETIME NULL COMMENT '分派时间',
    started_at DATETIME NULL COMMENT '开始处理时间',
    resolved_at DATETIME NULL COMMENT '解决时间',
    closed_at DATETIME NULL COMMENT '关闭时间',

    -- 处理方案
    solution TEXT COMMENT '处理方案',
    solution_type VARCHAR(32) COMMENT '方案类型',
    root_cause TEXT COMMENT '根因',

    -- SLA
    expected_resolve_time DATETIME COMMENT '期望解决时间',
    actual_resolve_duration INT COMMENT '实际解决耗时(分钟)',
    sla_breached BOOLEAN DEFAULT FALSE COMMENT '是否超时',

    -- 备注
    remark TEXT COMMENT '备注',

    -- 审计字段
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    INDEX idx_ticket_no (ticket_no),
    INDEX idx_exception (exception_record_id),
    INDEX idx_fingerprint (exception_fingerprint),
    INDEX idx_service (service_name, environment),
    INDEX idx_status (status, severity),
    INDEX idx_assignee (assignee),
    INDEX idx_created_at (created_at),
    INDEX idx_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警工单表';

-- ================================================
-- 3. 告警工单状态历史表
-- ================================================
DROP TABLE IF EXISTS app_alarm_ticket_history;

CREATE TABLE app_alarm_ticket_history (
    -- 主键
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    ticket_id BIGINT NOT NULL COMMENT '工单ID',
    ticket_no VARCHAR(64) NOT NULL COMMENT '工单编号',

    -- 状态变更
    from_status VARCHAR(32) COMMENT '原状态',
    to_status VARCHAR(32) NOT NULL COMMENT '新状态',

    -- 操作信息
    operator VARCHAR(64) NOT NULL COMMENT '操作人',
    operation_type VARCHAR(32) NOT NULL COMMENT '操作类型',
    comment TEXT COMMENT '操作备注',

    -- 审计字段
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 索引
    INDEX idx_ticket_id (ticket_id),
    INDEX idx_ticket_no (ticket_no),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警工单状态历史表';

-- ================================================
-- 验证表创建
-- ================================================
SHOW TABLES;

-- 验证 app_alarm_record 表结构
DESC app_alarm_record;

-- 验证 app_alarm_ticket 表结构
DESC app_alarm_ticket;

-- 验证 app_alarm_ticket_history 表结构
DESC app_alarm_ticket_history;

-- ================================================
-- 初始化完成提示
-- ================================================
SELECT '========================================' AS '';
SELECT '✅ One Agent 4J 数据库初始化完成!' AS 'Status';
SELECT '========================================' AS '';
SELECT '' AS '';
SELECT '已创建的表:' AS '';
SELECT '  1. app_alarm_record - 告警记录表' AS '';
SELECT '  2. app_alarm_ticket - 告警工单表' AS '';
SELECT '  3. app_alarm_ticket_history - 告警工单状态历史表' AS '';
SELECT '' AS '';
SELECT '数据库: one_agent' AS '';
SELECT '字符集: utf8mb4' AS '';
SELECT '排序规则: utf8mb4_unicode_ci' AS '';
SELECT '' AS '';
SELECT '========================================' AS '';
