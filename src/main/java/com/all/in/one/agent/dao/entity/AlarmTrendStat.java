package com.all.in.one.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 异常趋势统计实体
 * <p>
 * 用于存储异常数量的统计数据，支持按天和按小时两种粒度聚合。
 * 用于趋势分析、预警检测和未来预测。
 * </p>
 *
 * @author One Agent 4J
 */
@Data
@TableName("alarm_trend_stat")
public class AlarmTrendStat {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 统计日期
     * <p>
     * 示例：2024-11-15
     * </p>
     */
    private LocalDate statDate;

    /**
     * 统计小时(0-23)
     * <p>
     * 按小时统计时使用，按天统计时为null
     * </p>
     */
    private Integer statHour;

    /**
     * 服务名称
     * <p>
     * 示例：payment-service, order-service
     * </p>
     */
    private String serviceName;

    /**
     * 异常类型
     * <p>
     * 示例：NullPointerException, SQLException
     * </p>
     */
    private String exceptionType;

    /**
     * 环境
     * <p>
     * 取值：dev, test, prod
     * </p>
     */
    private String environment;

    /**
     * 异常数量
     * <p>
     * 该时间段内发生的异常总数
     * </p>
     */
    private Integer exceptionCount;

    /**
     * 唯一指纹数
     * <p>
     * 该时间段内不重复的异常指纹数量
     * </p>
     */
    private Integer uniqueFingerprintCount;

    /**
     * 影响用户数
     * <p>
     * 该时间段内受影响的用户数量（如果有记录）
     * </p>
     */
    private Integer affectedUserCount;

    /**
     * P0级别数量
     */
    private Integer p0Count;

    /**
     * P1级别数量
     */
    private Integer p1Count;

    /**
     * P2级别数量
     */
    private Integer p2Count;

    /**
     * P3级别数量
     */
    private Integer p3Count;

    /**
     * P4级别数量
     */
    private Integer p4Count;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 获取严重级别分布描述
     *
     * @return 级别分布字符串
     */
    public String getSeverityDistribution() {
        return String.format("P0:%d, P1:%d, P2:%d, P3:%d, P4:%d",
                p0Count != null ? p0Count : 0,
                p1Count != null ? p1Count : 0,
                p2Count != null ? p2Count : 0,
                p3Count != null ? p3Count : 0,
                p4Count != null ? p4Count : 0);
    }

    /**
     * 判断是否为小时级别统计
     *
     * @return true-小时统计，false-天统计
     */
    public boolean isHourlyStat() {
        return statHour != null;
    }
}
