# SQL 脚本说明

本目录包含 One Agent 4J 数据库相关的 SQL 脚本。

---

## 📁 文件列表

| 文件名 | 用途 | 说明 |
|--------|------|------|
| **`init.sql`** ⭐ | 完整初始化脚本 | **推荐使用** - 全新安装时使用,创建数据库和所有表 |
| `init_v2.sql` | 完整初始化脚本(带注释) | 与 init.sql 功能相同,包含更详细的注释和测试数据 |
| `migration_add_ai_fields.sql` | 迁移脚本 | 为已存在的数据库添加 AI 去噪相关字段 |
| `FIELD_MAPPING.md` | 字段映射文档 | Java 实体类与 MySQL 字段的完整对照表 |

---

## 🚀 使用指南

### 场景 1: 全新安装

如果你是第一次部署 One Agent 4J,使用 `init.sql`:

```bash
# 连接 MySQL
mysql -u root -p

# 执行初始化脚本
source sql/init.sql

# 或者使用重定向
mysql -u root -p < sql/init.sql
```

**执行后会创建**:
- 数据库: `one_agent`
- 表: `app_alarm_record`, `app_alarm_ticket`, `app_alarm_ticket_status_history`
- 索引: 所有必需的索引
- 字符集: utf8mb4

### 场景 2: 已有数据库(迁移)

如果你的数据库已存在,但缺少 AI 去噪字段,使用 `migration_add_ai_fields.sql`:

```bash
# 连接到已有数据库
mysql -u root -p one_agent

# 执行迁移脚本
source sql/migration_add_ai_fields.sql
```

**迁移脚本会添加**:
- `ai_processed` - AI 是否已处理
- `ai_decision` - AI 决策结果
- `ai_reason` - AI 决策原因
- `updated_at` - 更新时间(如果缺失)

---

## 📊 数据库结构

### 1. app_alarm_record (异常记录表)

存储所有捕获的异常信息。

**字段数**: 26 个
**主要字段**:
- 应用信息: app_name, environment, instance_id
- 异常信息: exception_type, exception_message, stack_trace, fingerprint
- 错误位置: error_class, error_method, error_line, error_location
- 请求信息: request_method, request_uri, request_params, client_ip
- 线程信息: thread_id, thread_name
- 链路追踪: trace_id, span_id
- 时间信息: occurred_at, reported_at
- AI 去噪: ai_processed, ai_decision, ai_reason

**索引**:
- `idx_app_env`: (app_name, environment)
- `idx_fingerprint`: (fingerprint) - 用于去重
- `idx_exception_type`: (exception_type)
- `idx_occurred_at`: (occurred_at)
- `idx_ai_processed`: (ai_processed)

### 2. app_alarm_ticket (工单表)

异常对应的处理工单。

**字段数**: 34 个
**主要字段**:
- 工单编号: app_alarm_ticket_no (唯一)
- 关联异常: app_alarm_record_id, exception_fingerprint
- 服务信息: service_name, environment
- 问题信息: title, problem_type, problem_category, severity
- 异常内容: exception_type, exception_message, stack_trace, error_location
- 发生情况: occurrence_count, first_occurred_at, last_occurred_at
- 责任人: service_owner, assignee, reporter
- 处理状态: status, progress
- 处理时间: assigned_at, started_at, resolved_at, closed_at
- 处理方案: solution, solution_type, root_cause
- SLA: expected_resolve_time, actual_resolve_duration, sla_breached

**索引**:
- `idx_app_alarm_ticket_no`: (app_alarm_ticket_no)
- `idx_app_alarm_record`: (app_alarm_record_id)
- `idx_fingerprint`: (exception_fingerprint)
- `idx_service_env`: (service_name, environment)
- `idx_status_severity`: (status, severity)
- `idx_assignee`: (assignee)
- `idx_severity`: (severity)

### 3. app_alarm_ticket_status_history (工单状态历史表)

工单状态变更历史,用于审计追踪。

**字段数**: 7 个
**主要字段**:
- 关联工单: app_alarm_ticket_id, app_alarm_ticket_no
- 状态变更: from_status, to_status
- 操作信息: operator, operation_type, comment

---

## 🔧 重要说明

### 1. 时间类型

**使用 DATETIME 而不是 TIMESTAMP**:

| Java 类型 | MySQL 类型 | 原因 |
|-----------|-----------|------|
| `LocalDateTime` | `DATETIME` | ✅ 支持 1000-9999 年,无时区问题 |
| `LocalDateTime` | ~~TIMESTAMP~~ | ❌ 只支持 1970-2038 年,有时区问题 |

### 2. 字符集

所有表使用 **utf8mb4** 字符集:
- 支持 emoji 和特殊字符
- 避免中文乱码
- 与 Java String 完美兼容

### 3. 驼峰命名转换

MyBatis-Plus 自动转换:

```
Java 驼峰     →  MySQL 下划线
appName       →  app_name
exceptionType →  exception_type
occurredAt    →  occurred_at
```

### 4. 字段约束

**NOT NULL 字段** (必填):
- `app_alarm_record`: app_name, environment, exception_type, fingerprint, occurred_at, reported_at
- `app_alarm_ticket`: app_alarm_ticket_no, app_alarm_record_id, service_name, environment, title, severity, status, first_occurred_at, last_occurred_at

**DEFAULT 值**:
- `ai_processed`: FALSE
- `occurrence_count`: 1
- `status`: 'PENDING'
- `progress`: 0
- `sla_breached`: FALSE
- `reporter`: 'AI-Agent'
- `created_at`: CURRENT_TIMESTAMP
- `updated_at`: CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP

---

## ✅ 验证安装

执行以下 SQL 验证安装是否成功:

```sql
-- 1. 检查数据库是否存在
SHOW DATABASES LIKE 'one_agent';

-- 2. 切换到数据库
USE one_agent;

-- 3. 检查表是否创建
SHOW TABLES;
-- 预期: app_alarm_record, app_alarm_ticket, app_alarm_ticket_status_history

-- 4. 检查 app_alarm_record 字段数
SELECT COUNT(*) AS field_count
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'one_agent' AND TABLE_NAME = 'app_alarm_record';
-- 预期: 26

-- 5. 检查 app_alarm_ticket 字段数
SELECT COUNT(*) AS field_count
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'one_agent' AND TABLE_NAME = 'app_alarm_ticket';
-- 预期: 34

-- 6. 查看表结构
DESC app_alarm_record;
DESC app_alarm_ticket;

-- 7. 检查 AI 字段是否存在
SHOW COLUMNS FROM app_alarm_record LIKE 'ai_%';
-- 预期: ai_processed, ai_decision, ai_reason

-- 8. 检查索引
SHOW INDEX FROM app_alarm_record;
SHOW INDEX FROM app_alarm_ticket;
```

---

## 🐛 常见问题

### Q1: 执行 init.sql 报错 "Unknown database 'one_agent'"

**原因**: 数据库尚未创建
**解决**: 脚本会自动创建数据库,确保以 root 用户执行

### Q2: 字段类型不匹配错误

**原因**: 旧版本 DDL 使用 TIMESTAMP
**解决**: 使用最新的 `init.sql` (v2.0.0),已全部改为 DATETIME

### Q3: 中文乱码

**原因**: 字符集不是 utf8mb4
**解决**:
```sql
ALTER DATABASE one_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE app_alarm_record CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE app_alarm_ticket CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Q4: MyBatis 找不到字段

**原因**: 驼峰命名未启用
**解决**: 在 `application.properties` 中添加:
```properties
mybatis-plus.configuration.map-underscore-to-camel-case=true
```

---

## 🔄 版本历史

| 版本 | 日期 | 变更内容 |
|------|------|---------|
| 1.0.0 | 2025-11-07 | 初始版本,3 张表 |
| 2.0.0 | 2025-11-07 | 所有时间字段从 TIMESTAMP 改为 DATETIME,完全匹配 Java 实体类 |

---

## 📚 相关文档

- `FIELD_MAPPING.md` - Java 实体类与 MySQL 字段完整对照表
- `../CLAUDE.md` - 项目整体架构说明
- `../TESTING_GUIDE.md` - 测试指南

---

## 💡 下一步

完成数据库初始化后:

1. **配置数据库连接** - 编辑 `application.properties`:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/one_agent
   spring.datasource.username=root
   spring.datasource.password=your_password
   ```

2. **启动应用**:
   ```bash
   mvn spring-boot:run
   ```

3. **测试异常捕获**:
   ```bash
   curl http://localhost:8080/test/null-pointer
   ```

4. **查看数据库**:
   ```sql
   SELECT * FROM app_alarm_record ORDER BY occurred_at DESC LIMIT 10;
   SELECT * FROM app_alarm_ticket ORDER BY created_at DESC LIMIT 10;
   ```

祝使用愉快! 🎉
