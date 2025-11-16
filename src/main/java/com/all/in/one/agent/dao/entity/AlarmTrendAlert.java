package com.all.in.one.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 趋势预警记录实体
 * <p>
 * 当检测到异常趋势异常时（如数量突增、持续上升等），自动创建预警记录。
 * 支持人工确认或忽略处理。
 * </p>
 *
 * @author One Agent 4J
 */
@Data
@TableName("alarm_trend_alert")
public class AlarmTrendAlert {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 预警类型
     * <p>
     * 取值：
     * - INCREASE: 异常数量上升
     * - DECREASE: 异常数量下降
     * - PEAK: 高峰异常
     * - ANOMALY: 异常波动
     * </p>
     */
    private String alertType;

    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 异常类型
     */
    private String exceptionType;

    /**
     * 之前数量
     * <p>
     * 对比基准时间点的异常数量
     * </p>
     */
    private Integer previousCount;

    /**
     * 当前数量
     * <p>
     * 当前时间点的异常数量
     * </p>
     */
    private Integer currentCount;

    /**
     * 变化率(%)
     * <p>
     * 计算公式：(currentCount - previousCount) / previousCount * 100
     * </p>
     */
    private BigDecimal changeRate;

    /**
     * 预警标题
     * <p>
     * 示例：payment-service 异常数量异常增长
     * </p>
     */
    private String alertTitle;

    /**
     * 预警详情
     * <p>
     * 包含详细的趋势分析、未来预测和建议措施
     * </p>
     */
    private String alertMessage;

    /**
     * 预警级别
     * <p>
     * 取值：
     * - WARNING: 警告（变化率 20%-50%）
     * - ERROR: 错误（变化率 50%-100%）
     * - CRITICAL: 严重（变化率 >100%）
     * </p>
     */
    private String severity;

    /**
     * 未来7天预测数据(JSON)
     * <p>
     * 格式：{"2024-11-16": 92, "2024-11-17": 98, ...}
     * </p>
     */
    private String predictionData;

    /**
     * 状态
     * <p>
     * 取值：
     * - PENDING: 待处理
     * - CONFIRMED: 已确认
     * - IGNORED: 已忽略
     * </p>
     */
    private String status;

    /**
     * 处理人工号
     */
    private String handledBy;

    /**
     * 处理时间
     */
    private LocalDateTime handledAt;

    /**
     * 处理备注
     */
    private String handleComment;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 判断是否为高优先级预警
     *
     * @return true-高优先级（ERROR或CRITICAL）
     */
    public boolean isHighPriority() {
        return "ERROR".equals(severity) || "CRITICAL".equals(severity);
    }

    /**
     * 判断是否已处理
     *
     * @return true-已处理
     */
    public boolean isHandled() {
        return "CONFIRMED".equals(status) || "IGNORED".equals(status);
    }

    /**
     * 获取变化率绝对值
     *
     * @return 变化率绝对值
     */
    public BigDecimal getAbsChangeRate() {
        return changeRate != null ? changeRate.abs() : BigDecimal.ZERO;
    }
}
