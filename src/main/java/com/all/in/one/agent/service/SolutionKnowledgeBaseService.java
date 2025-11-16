package com.all.in.one.agent.service;

import com.all.in.one.agent.dao.entity.AlarmSolutionKb;
import com.all.in.one.agent.dao.entity.AppAlarmRecord;
import com.all.in.one.agent.dao.mapper.AlarmSolutionKbMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 解决方案知识库服务
 * <p>
 * 提供历史解决方案查询、保存和使用统计功能。
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Service
public class SolutionKnowledgeBaseService {

    private final AlarmSolutionKbMapper solutionKbMapper;

    public SolutionKnowledgeBaseService(AlarmSolutionKbMapper solutionKbMapper) {
        this.solutionKbMapper = solutionKbMapper;
        log.info("SolutionKnowledgeBaseService 初始化完成");
    }

    /**
     * 根据告警记录查找相关解决方案
     * <p>
     * 查询逻辑：
     * 1. 首先按异常指纹精确匹配
     * 2. 如果没有精确匹配，按服务名称和异常类型模糊匹配
     * </p>
     *
     * @param alarmRecord 告警记录
     * @return 相关解决方案列表（按相关性排序）
     */
    public List<AlarmSolutionKb> findRelatedSolutions(AppAlarmRecord alarmRecord) {
        if (alarmRecord == null) {
            log.warn("告警记录为空，无法查找解决方案");
            return List.of();
        }

        try {
            // 1. 首先按指纹精确匹配
            List<AlarmSolutionKb> exactMatches = solutionKbMapper
                    .selectByFingerprint(alarmRecord.getFingerprint());

            if (!exactMatches.isEmpty()) {
                log.info("找到精确匹配的解决方案 {} 个 - 指纹: {}",
                        exactMatches.size(), alarmRecord.getFingerprint());
                return exactMatches;
            }

            // 2. 如果没有精确匹配，按服务名称和异常类型模糊匹配
            List<AlarmSolutionKb> similarSolutions = solutionKbMapper
                    .selectSimilarSolutions(
                            alarmRecord.getAppName(),
                            alarmRecord.getExceptionType(),
                            5
                    );

            if (!similarSolutions.isEmpty()) {
                log.info("找到相似解决方案 {} 个 - 服务: {}, 异常类型: {}",
                        similarSolutions.size(),
                        alarmRecord.getAppName(),
                        alarmRecord.getExceptionType());
            } else {
                log.debug("未找到相关解决方案 - 服务: {}, 异常类型: {}",
                        alarmRecord.getAppName(),
                        alarmRecord.getExceptionType());
            }

            return similarSolutions;

        } catch (Exception e) {
            log.error("查找相关解决方案失败 - 指纹: {}, 错误: {}",
                    alarmRecord.getFingerprint(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 保存解决方案到知识库
     *
     * @param solution 解决方案
     * @return 保存的方案ID
     */
    public Long saveSolution(AlarmSolutionKb solution) {
        if (solution == null) {
            log.warn("解决方案对象为空，无法保存");
            return null;
        }

        try {
            // 初始化统计字段
            if (solution.getUsageCount() == null) {
                solution.setUsageCount(0);
            }
            if (solution.getSuccessCount() == null) {
                solution.setSuccessCount(0);
            }
            if (solution.getEffectivenessScore() == null) {
                solution.setEffectivenessScore(0);
            }

            solutionKbMapper.insert(solution);
            log.info("解决方案已保存到知识库 - ID: {}, 指纹: {}",
                    solution.getId(), solution.getExceptionFingerprint());
            return solution.getId();

        } catch (Exception e) {
            log.error("保存解决方案失败 - 指纹: {}, 错误: {}",
                    solution.getExceptionFingerprint(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 更新方案使用次数
     *
     * @param solutionId 方案ID
     */
    public void incrementUsageCount(Long solutionId) {
        if (solutionId == null) {
            log.warn("方案ID为空，无法更新使用次数");
            return;
        }

        try {
            AlarmSolutionKb solution = solutionKbMapper.selectById(solutionId);
            if (solution != null) {
                solution.setUsageCount(solution.getUsageCount() + 1);
                solutionKbMapper.updateById(solution);
                log.debug("更新方案使用次数 - ID: {}, 使用次数: {}",
                        solutionId, solution.getUsageCount());
            } else {
                log.warn("未找到方案 - ID: {}", solutionId);
            }
        } catch (Exception e) {
            log.error("更新使用次数失败 - ID: {}, 错误: {}", solutionId, e.getMessage(), e);
        }
    }

    /**
     * 更新方案成功次数并自动更新评分
     *
     * @param solutionId 方案ID
     */
    public void incrementSuccessCount(Long solutionId) {
        if (solutionId == null) {
            log.warn("方案ID为空，无法更新成功次数");
            return;
        }

        try {
            AlarmSolutionKb solution = solutionKbMapper.selectById(solutionId);
            if (solution != null) {
                solution.setSuccessCount(solution.getSuccessCount() + 1);

                // 自动更新有效性评分（基于成功率）
                int totalUsage = solution.getUsageCount();
                int successUsage = solution.getSuccessCount();
                if (totalUsage > 0) {
                    double successRate = (double) successUsage / totalUsage;
                    int newScore = (int) Math.ceil(successRate * 5); // 1-5星
                    solution.setEffectivenessScore(newScore);
                }

                solutionKbMapper.updateById(solution);
                log.info("更新方案成功次数 - ID: {}, 成功次数: {}, 评分: {}",
                        solutionId, solution.getSuccessCount(), solution.getEffectivenessScore());
            } else {
                log.warn("未找到方案 - ID: {}", solutionId);
            }
        } catch (Exception e) {
            log.error("更新成功次数失败 - ID: {}, 错误: {}", solutionId, e.getMessage(), e);
        }
    }

    /**
     * 查询高评分方案（用于推荐）
     *
     * @param limit 返回数量
     * @return 高评分方案列表
     */
    public List<AlarmSolutionKb> getTopRatedSolutions(int limit) {
        try {
            return solutionKbMapper.selectTopRatedSolutions(limit);
        } catch (Exception e) {
            log.error("查询高评分方案失败 - 错误: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
