-- ================================================
-- 数据库迁移脚本: 添加 AI 去噪相关字段
-- ================================================
-- 执行时间: 2025-11-07
-- 说明: 为 app_alarm_record 表添加 AI 去噪功能所需的字段
-- ================================================

USE one_agent;

-- 检查并添加 updated_at 字段
ALTER TABLE app_alarm_record
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
AFTER created_at;

-- 检查并添加 ai_processed 字段
ALTER TABLE app_alarm_record
ADD COLUMN IF NOT EXISTS ai_processed BOOLEAN DEFAULT FALSE COMMENT 'AI是否已处理'
AFTER updated_at;

-- 检查并添加 ai_decision 字段
ALTER TABLE app_alarm_record
ADD COLUMN IF NOT EXISTS ai_decision VARCHAR(32) COMMENT 'AI决策结果: ALERT/IGNORE'
AFTER ai_processed;

-- 检查并添加 ai_reason 字段
ALTER TABLE app_alarm_record
ADD COLUMN IF NOT EXISTS ai_reason TEXT COMMENT 'AI决策原因'
AFTER ai_decision;

-- 验证字段是否添加成功
SELECT
    COLUMN_NAME,
    COLUMN_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'one_agent'
  AND TABLE_NAME = 'app_alarm_record'
  AND COLUMN_NAME IN ('updated_at', 'ai_processed', 'ai_decision', 'ai_reason')
ORDER BY ORDINAL_POSITION;

-- 完成提示
SELECT 'AI 字段迁移完成! ✅' AS status;
