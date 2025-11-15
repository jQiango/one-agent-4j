-- ================================================
-- One Agent 4J 数据库初始化脚本 V2
-- ================================================
-- 版本: 2.0.0
-- 更新时间: 2025-11-07
-- 说明: 基于最新 POJO 对象生成,完全匹配 Java 实体类字段
-- ================================================
-- 实体类对应:
--   - AppAlarmRecord.java → app_alarm_record
--   - AppAlarmTicket.java → app_alarm_app_alarm_ticket
-- ================================================

-- 删除已存在的数据库 (谨慎使用!)
-- DROP DATABASE IF EXISTS one_agent;

-- 创建数据库
CREATE DATABASE IF NOT EXISTS one_agent
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE one_agent;

-- ================================================
-- 1. 告警记录表 (app_alarm_record)
-- ================================================
-- 对应实体类: com.all.in.one.agent.dao.entity.AppAlarmRecord
-- ================================================
DROP TABLE IF EXISTS app_alarm_record;

CREATE TABLE app_alarm_record (
    -- ==================== 主键 ====================
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    -- ==================== 应用信息 ====================
    app_name VARCHAR(128) NOT NULL COMMENT '应用名称',
    environment VARCHAR(32) NOT NULL COMMENT '环境: dev/test/uat/prod',
    instance_id VARCHAR(128) COMMENT '实例ID',
    hostname VARCHAR(128) COMMENT '主机名',
    ip VARCHAR(64) COMMENT 'IP地址',

    -- ==================== 异常信息 ====================
    exception_type VARCHAR(255) NOT NULL COMMENT '异常类型',
    exception_message TEXT COMMENT '异常消息',
    stack_trace TEXT COMMENT '完整堆栈',
    fingerprint VARCHAR(64) NOT NULL COMMENT '异常指纹(MD5)',

    -- ==================== 错误位置 ====================
    error_class VARCHAR(255) COMMENT '错误类名',
    error_method VARCHAR(255) COMMENT '错误方法名',
    error_line INT COMMENT '错误行号',
    error_location VARCHAR(512) COMMENT '错误位置: 类名.方法名:行号',

    -- ==================== 请求信息 ====================
    request_method VARCHAR(16) COMMENT 'HTTP方法: GET/POST/PUT/DELETE',
    request_uri VARCHAR(512) COMMENT '请求URI',
    request_params TEXT COMMENT '请求参数(JSON格式)',
    client_ip VARCHAR(64) COMMENT '客户端IP',

    -- ==================== 线程信息 ====================
    thread_id BIGINT COMMENT '线程ID',
    thread_name VARCHAR(255) COMMENT '线程名称',

    -- ==================== 链路追踪 ====================
    trace_id VARCHAR(64) COMMENT 'TraceId - 全局追踪ID',
    span_id VARCHAR(64) COMMENT 'SpanId - 跨度ID',

    -- ==================== 时间信息 ====================
    occurred_at DATETIME NOT NULL COMMENT '异常发生时间',
    reported_at DATETIME NOT NULL COMMENT '异常上报时间',

    -- ==================== AI 去噪相关 ====================
    ai_processed BOOLEAN DEFAULT FALSE COMMENT 'AI是否已处理',
    ai_decision VARCHAR(32) COMMENT 'AI决策结果: shouldAlert=true/false',
    ai_reason TEXT COMMENT 'AI决策原因',

    -- ==================== 审计字段 ====================
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',

    -- ==================== 索引 ====================
    INDEX idx_app_env (app_name, environment) COMMENT '应用+环境组合索引',
    INDEX idx_fingerprint (fingerprint) COMMENT '指纹索引(用于去重)',
    INDEX idx_exception_type (exception_type) COMMENT '异常类型索引',
    INDEX idx_occurred_at (occurred_at) COMMENT '发生时间索引(用于时间范围查询)',
    INDEX idx_created_at (created_at) COMMENT '创建时间索引',
    INDEX idx_ai_processed (ai_processed) COMMENT 'AI处理状态索引'

) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci
COMMENT='异常记录表 - 存储所有捕获的异常信息';

-- ================================================
-- 2. 工单表 (app_alarm_ticket)
-- ================================================
-- 对应实体类: com.all.in.one.agent.dao.entity.Ticket
-- ================================================
DROP TABLE IF EXISTS app_alarm_ticket;

CREATE TABLE app_alarm_ticket (
    -- ==================== 主键 ====================
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单ID',

    -- ==================== 工单编号 ====================
    app_alarm_ticket_no VARCHAR(64) UNIQUE NOT NULL COMMENT '工单编号(唯一)',

    -- ==================== 关联异常 ====================
    app_alarm_record_id BIGINT NOT NULL COMMENT '关联的异常记录ID',
    exception_fingerprint VARCHAR(64) NOT NULL COMMENT '异常指纹',

    -- ==================== 服务信息 ====================
    service_name VARCHAR(128) NOT NULL COMMENT '服务名称',
    environment VARCHAR(32) NOT NULL COMMENT '环境',

    -- ==================== 问题信息 ====================
    title VARCHAR(255) NOT NULL COMMENT '工单标题',
    problem_type VARCHAR(64) NOT NULL COMMENT '问题类型',
    problem_category VARCHAR(32) NOT NULL COMMENT '问题分类',
    severity VARCHAR(16) NOT NULL COMMENT '严重级别: P0/P1/P2/P3/P4',

    -- ==================== 异常内容 ====================
    exception_type VARCHAR(255) NOT NULL COMMENT '异常类型',
    exception_message TEXT COMMENT '异常消息',
    stack_trace TEXT COMMENT '堆栈信息',
    error_location VARCHAR(512) COMMENT '错误位置',
    occurrence_count INT DEFAULT 1 COMMENT '发生次数(同一指纹)',
    first_occurred_at DATETIME NOT NULL COMMENT '首次发生时间',
    last_occurred_at DATETIME NOT NULL COMMENT '最后发生时间',

    -- ==================== 责任人 ====================
    service_owner VARCHAR(64) COMMENT '服务负责人',
    assignee VARCHAR(64) COMMENT '当前处理人',
    reporter VARCHAR(64) DEFAULT 'AI-Agent' COMMENT '报告人',

    -- ==================== 处理状态 ====================
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '工单状态: PENDING/ASSIGNED/IN_PROGRESS/RESOLVED/CLOSED',
    progress INT DEFAULT 0 COMMENT '处理进度 0-100',

    -- ==================== 处理时间 ====================
    assigned_at DATETIME NULL COMMENT '分派时间',
    started_at DATETIME NULL COMMENT '开始处理时间',
    resolved_at DATETIME NULL COMMENT '解决时间',
    closed_at DATETIME NULL COMMENT '关闭时间',

    -- ==================== 处理方案 ====================
    solution TEXT COMMENT '处理方案',
    solution_type VARCHAR(32) COMMENT '方案类型: CODE_FIX/CONFIG_CHANGE/KNOWN_ISSUE/WONTFIX',
    root_cause TEXT COMMENT '根本原因分析',

    -- ==================== SLA ====================
    expected_resolve_time DATETIME COMMENT '期望解决时间(基于severity计算)',
    actual_resolve_duration INT COMMENT '实际解决耗时(分钟)',
    sla_breached BOOLEAN DEFAULT FALSE COMMENT '是否超时',

    -- ==================== 备注 ====================
    remark TEXT COMMENT '备注',

    -- ==================== 审计字段 ====================
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '工单创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '工单更新时间',

    -- ==================== 索引 ====================
    INDEX idx_app_alarm_ticket_no (app_alarm_ticket_no) COMMENT '工单编号索引',
    INDEX idx_app_alarm_record (app_alarm_record_id) COMMENT '异常记录关联索引',
    INDEX idx_fingerprint (exception_fingerprint) COMMENT '指纹索引(用于查找同类工单)',
    INDEX idx_service_env (service_name, environment) COMMENT '服务+环境组合索引',
    INDEX idx_status_severity (status, severity) COMMENT '状态+严重程度组合索引',
    INDEX idx_assignee (assignee) COMMENT '处理人索引',
    INDEX idx_created_at (created_at) COMMENT '创建时间索引',
    INDEX idx_severity (severity) COMMENT '严重程度索引'

) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci
COMMENT='工单表 - 异常对应的处理工单';

-- ================================================
-- 3. 工单状态历史表 (app_alarm_ticket_status_history) - 可选
-- ================================================
-- 说明: 用于记录工单状态变更历史,实现审计追踪
-- 注意: 当前版本没有对应的实体类,可根据需要创建
-- ================================================
DROP TABLE IF EXISTS app_alarm_ticket_status_history;

CREATE TABLE app_alarm_ticket_status_history (
    -- ==================== 主键 ====================
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    -- ==================== 关联工单 ====================
    app_alarm_ticket_id BIGINT NOT NULL COMMENT '工单ID',
    app_alarm_ticket_no VARCHAR(64) NOT NULL COMMENT '工单编号',

    -- ==================== 状态变更 ====================
    from_status VARCHAR(32) COMMENT '原状态',
    to_status VARCHAR(32) NOT NULL COMMENT '新状态',

    -- ==================== 操作信息 ====================
    operator VARCHAR(64) NOT NULL COMMENT '操作人',
    operation_type VARCHAR(32) NOT NULL COMMENT '操作类型: CREATE/ASSIGN/START/RESOLVE/CLOSE/REOPEN',
    comment TEXT COMMENT '操作备注',

    -- ==================== 审计字段 ====================
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',

    -- ==================== 索引 ====================
    INDEX idx_app_alarm_ticket_id (app_alarm_ticket_id) COMMENT '工单ID索引',
    INDEX idx_app_alarm_ticket_no (app_alarm_ticket_no) COMMENT '工单编号索引',
    INDEX idx_created_at (created_at) COMMENT '创建时间索引'

) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci
COMMENT='工单状态历史表 - 记录工单状态变更轨迹';

-- ================================================
-- 验证表创建
-- ================================================
SHOW TABLES;

-- 查看 app_alarm_record 表结构
DESC app_alarm_record;

-- 查看 app_alarm_ticket 表结构
DESC app_alarm_ticket;

-- 查看 app_alarm_ticket_status_history 表结构
DESC app_alarm_ticket_status_history;

-- ================================================
-- 数据字典视图 (可选)
-- ================================================
-- 查看所有表的字段数量
SELECT
    TABLE_NAME AS '表名',
    TABLE_COMMENT AS '表说明',
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'one_agent'
     AND TABLE_NAME = t.TABLE_NAME) AS '字段数'
FROM information_schema.TABLES t
WHERE TABLE_SCHEMA = 'one_agent'
ORDER BY TABLE_NAME;

-- ================================================
-- 初始化测试数据 (可选)
-- ================================================
-- 插入一条测试异常记录
INSERT INTO app_alarm_record (
    app_name, environment, exception_type, exception_message,
    fingerprint, error_location, occurred_at, reported_at
) VALUES (
    'one-agent-4j', 'dev', 'java.lang.NullPointerException',
    'Test exception for initialization',
    MD5('test_fingerprint_001'),
    'com.test.TestClass.testMethod:123',
    NOW(), NOW()
);

-- 插入一条测试工单
INSERT INTO app_alarm_ticket (
    app_alarm_ticket_no, app_alarm_record_id, exception_fingerprint,
    service_name, environment, title, problem_type, problem_category,
    severity, exception_type, exception_message, error_location,
    first_occurred_at, last_occurred_at
) VALUES (
    CONCAT('TK-', DATE_FORMAT(NOW(), '%Y%m%d'), '-00001'),
    LAST_INSERT_ID(),
    MD5('test_fingerprint_001'),
    'one-agent-4j', 'dev', 'Test Exception - NullPointerException',
    'Runtime Exception', 'Code Bug', 'P3',
    'java.lang.NullPointerException', 'Test exception for initialization',
    'com.test.TestClass.testMethod:123',
    NOW(), NOW()
);

-- 验证测试数据
SELECT '========================================' AS '';
SELECT '✅ 测试数据插入成功!' AS 'Status';
SELECT COUNT(*) AS 'app_alarm_record 记录数' FROM app_alarm_record;
SELECT COUNT(*) AS 'app_alarm_ticket 记录数' FROM app_alarm_ticket;
SELECT '========================================' AS '';

-- ================================================
-- 初始化完成提示
-- ================================================
SELECT '========================================' AS '';
SELECT '✅ One Agent 4J 数据库初始化完成!' AS 'Status';
SELECT '========================================' AS '';
SELECT '' AS '';
SELECT '已创建的表:' AS '';
SELECT '  1. app_alarm_record - 异常记录表 (26 字段)' AS '';
SELECT '  2. app_alarm_ticket - 工单表 (34 字段)' AS '';
SELECT '  3. app_alarm_ticket_status_history - 工单状态历史表 (7 字段)' AS '';
SELECT '' AS '';
SELECT '数据库配置:' AS '';
SELECT '  - 数据库: one_agent' AS '';
SELECT '  - 字符集: utf8mb4' AS '';
SELECT '  - 排序规则: utf8mb4_unicode_ci' AS '';
SELECT '  - 存储引擎: InnoDB' AS '';
SELECT '' AS '';
SELECT '字段类型说明:' AS '';
SELECT '  - DATETIME: 用于时间字段(精确到秒)' AS '';
SELECT '  - TEXT: 用于长文本(异常消息、堆栈等)' AS '';
SELECT '  - VARCHAR: 用于短文本(有长度限制)' AS '';
SELECT '  - BOOLEAN: 用于布尔值(TINYINT(1))' AS '';
SELECT '' AS '';
SELECT '索引优化:' AS '';
SELECT '  - 组合索引: app_name+environment, service_name+environment' AS '';
SELECT '  - 时间索引: occurred_at, created_at (支持范围查询)' AS '';
SELECT '  - 业务索引: fingerprint, status+severity, assignee' AS '';
SELECT '' AS '';
SELECT '下一步:' AS '';
SELECT '  1. 在 application.properties 中配置数据库连接' AS '';
SELECT '  2. 启动应用: mvn spring-boot:run' AS '';
SELECT '  3. 访问测试接口触发异常' AS '';
SELECT '  4. 查看监控统计: curl http://localhost:8080/api/v1/denoise/stats' AS '';
SELECT '' AS '';
SELECT '========================================' AS '';
