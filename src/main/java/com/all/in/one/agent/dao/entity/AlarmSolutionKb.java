package com.all.in.one.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警解决方案知识库实体
 * <p>
 * 存储历史告警的解决方案，支持方案推荐和知识复用。
 * </p>
 *
 * @author One Agent 4J
 */
@Data
@TableName("alarm_solution_kb")
public class AlarmSolutionKb {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 异常指纹
     * <p>
     * 用于精确匹配相同异常
     * </p>
     */
    private String exceptionFingerprint;

    /**
     * 异常类型
     * <p>
     * 如：java.lang.NullPointerException
     * </p>
     */
    private String exceptionType;

    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 问题摘要
     * <p>
     * 简短描述问题，如："支付接口空指针异常"
     * </p>
     */
    private String problemSummary;

    /**
     * 问题现象描述
     * <p>
     * 详细描述问题的表现形式
     * </p>
     */
    private String problemSymptoms;

    /**
     * 影响范围
     * <p>
     * 如："支付失败 35 次/分钟"
     * </p>
     */
    private String problemImpact;

    /**
     * 解决方案类型
     * <p>
     * 枚举值：CODE_FIX, CONFIG_CHANGE, ROLLBACK, RESTART, OTHER
     * </p>
     */
    private String solutionType;

    /**
     * 解决步骤
     * <p>
     * Markdown格式，详细记录解决步骤
     * </p>
     */
    private String solutionSteps;

    /**
     * 代码修复示例
     * <p>
     * 可选字段，存储修复代码片段
     * </p>
     */
    private String solutionCode;

    /**
     * 根本原因分析
     */
    private String rootCause;

    /**
     * 预防措施
     */
    private String preventMeasures;

    /**
     * 解决人工号
     */
    private String resolver;

    /**
     * 解决人姓名
     */
    private String resolverName;

    /**
     * 解决时间
     */
    private LocalDateTime resolvedAt;

    /**
     * 解决耗时（分钟）
     */
    private Integer resolveDurationMinutes;

    /**
     * 方案有效性评分
     * <p>
     * 1-5星，根据使用次数和成功率自动计算
     * </p>
     */
    private Integer effectivenessScore;

    /**
     * 方案被使用次数
     * <p>
     * 记录该方案被查看或应用的次数
     * </p>
     */
    private Integer usageCount;

    /**
     * 方案成功解决次数
     * <p>
     * 记录用户反馈该方案成功解决问题的次数
     * </p>
     */
    private Integer successCount;

    /**
     * 是否由AI提取
     * <p>
     * true: AI自动提取，false: 人工录入
     * </p>
     */
    private Boolean aiExtracted;

    /**
     * AI提取置信度
     * <p>
     * 0.0 - 1.0，表示AI提取的可信度
     * </p>
     */
    private Double aiConfidence;

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
     * 计算成功率
     *
     * @return 成功率（0.0-1.0），如果未使用则返回0
     */
    public double getSuccessRate() {
        if (usageCount == null || usageCount == 0) {
            return 0.0;
        }
        if (successCount == null) {
            return 0.0;
        }
        return (double) successCount / usageCount;
    }
}
