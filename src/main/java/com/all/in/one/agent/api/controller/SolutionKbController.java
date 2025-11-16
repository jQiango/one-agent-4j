package com.all.in.one.agent.api.controller;

import com.all.in.one.agent.dao.entity.AlarmSolutionKb;
import com.all.in.one.agent.dao.entity.AppAlarmRecord;
import com.all.in.one.agent.dao.mapper.AppAlarmRecordMapper;
import com.all.in.one.agent.service.SolutionKnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 解决方案知识库 REST API
 * <p>
 * 提供解决方案的查询、保存、反馈功能，用于管理界面和工单处理界面。
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@RestController
@RequestMapping("/api/solution-kb")
public class SolutionKbController {

    private final SolutionKnowledgeBaseService kbService;
    private final AppAlarmRecordMapper alarmRecordMapper;

    public SolutionKbController(
            SolutionKnowledgeBaseService kbService,
            AppAlarmRecordMapper alarmRecordMapper) {
        this.kbService = kbService;
        this.alarmRecordMapper = alarmRecordMapper;
    }

    /**
     * 根据告警记录ID查找相关解决方案
     * <p>
     * 用于工单处理页面显示历史解决方案推荐。
     * </p>
     *
     * @param alarmId 告警记录ID
     * @return 相关解决方案列表
     */
    @GetMapping("/find-by-alarm/{alarmId}")
    public Map<String, Object> findByAlarmId(@PathVariable Long alarmId) {
        Map<String, Object> result = new HashMap<>();

        try {
            AppAlarmRecord alarm = alarmRecordMapper.selectById(alarmId);
            if (alarm == null) {
                log.warn("告警记录不存在 - ID: {}", alarmId);
                result.put("success", false);
                result.put("message", "告警记录不存在");
                result.put("data", List.of());
                return result;
            }

            List<AlarmSolutionKb> solutions = kbService.findRelatedSolutions(alarm);
            log.info("查找到相关解决方案 {} 个 - 告警ID: {}", solutions.size(), alarmId);

            result.put("success", true);
            result.put("message", "查询成功");
            result.put("data", solutions);
            result.put("count", solutions.size());

        } catch (Exception e) {
            log.error("查找解决方案失败 - 告警ID: {}, 错误: {}", alarmId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
            result.put("data", List.of());
        }

        return result;
    }

    /**
     * 查询高评分方案（推荐）
     * <p>
     * 用于知识库首页展示优质方案。
     * </p>
     *
     * @param limit 返回数量，默认10
     * @return 高评分方案列表
     */
    @GetMapping("/top-rated")
    public Map<String, Object> getTopRated(
            @RequestParam(defaultValue = "10") int limit) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<AlarmSolutionKb> solutions = kbService.getTopRatedSolutions(limit);
            log.info("查询高评分方案 {} 个", solutions.size());

            result.put("success", true);
            result.put("message", "查询成功");
            result.put("data", solutions);
            result.put("count", solutions.size());

        } catch (Exception e) {
            log.error("查询高评分方案失败 - 错误: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
            result.put("data", List.of());
        }

        return result;
    }

    /**
     * 保存解决方案
     * <p>
     * 用于工单关闭时补充解决方案到知识库。
     * </p>
     *
     * @param solution 解决方案对象
     * @return 保存结果
     */
    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody AlarmSolutionKb solution) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long solutionId = kbService.saveSolution(solution);

            if (solutionId != null) {
                log.info("保存解决方案成功 - ID: {}, 指纹: {}",
                        solutionId, solution.getExceptionFingerprint());
                result.put("success", true);
                result.put("message", "保存成功");
                result.put("solutionId", solutionId);
            } else {
                result.put("success", false);
                result.put("message", "保存失败");
            }

        } catch (Exception e) {
            log.error("保存解决方案失败 - 指纹: {}, 错误: {}",
                    solution.getExceptionFingerprint(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 标记方案有用（增加使用次数和成功次数）
     * <p>
     * 用于工单处理人员反馈方案有效。
     * </p>
     *
     * @param solutionId 方案ID
     * @return 操作结果
     */
    @PostMapping("/mark-helpful/{solutionId}")
    public Map<String, Object> markHelpful(@PathVariable Long solutionId) {
        Map<String, Object> result = new HashMap<>();

        try {
            kbService.incrementUsageCount(solutionId);
            kbService.incrementSuccessCount(solutionId);

            log.info("标记方案有用 - ID: {}", solutionId);

            result.put("success", true);
            result.put("message", "已标记为有用");

        } catch (Exception e) {
            log.error("标记方案有用失败 - ID: {}, 错误: {}", solutionId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "操作失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 标记方案无用（仅增加使用次数）
     * <p>
     * 用于工单处理人员反馈方案无效，不增加成功次数，会降低评分。
     * </p>
     *
     * @param solutionId 方案ID
     * @return 操作结果
     */
    @PostMapping("/mark-not-helpful/{solutionId}")
    public Map<String, Object> markNotHelpful(@PathVariable Long solutionId) {
        Map<String, Object> result = new HashMap<>();

        try {
            kbService.incrementUsageCount(solutionId);

            log.info("标记方案无用 - ID: {}", solutionId);

            result.put("success", true);
            result.put("message", "已标记为无用");

        } catch (Exception e) {
            log.error("标记方案无用失败 - ID: {}, 错误: {}", solutionId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "操作失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 根据指纹查询解决方案
     * <p>
     * 用于精确查找某个异常的历史解决方案。
     * </p>
     *
     * @param fingerprint 异常指纹
     * @return 解决方案列表
     */
    @GetMapping("/find-by-fingerprint")
    public Map<String, Object> findByFingerprint(@RequestParam String fingerprint) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 创建临时告警记录对象用于查询
            AppAlarmRecord tempAlarm = new AppAlarmRecord();
            tempAlarm.setFingerprint(fingerprint);

            List<AlarmSolutionKb> solutions = kbService.findRelatedSolutions(tempAlarm);
            log.info("根据指纹查找到解决方案 {} 个 - 指纹: {}", solutions.size(), fingerprint);

            result.put("success", true);
            result.put("message", "查询成功");
            result.put("data", solutions);
            result.put("count", solutions.size());

        } catch (Exception e) {
            log.error("根据指纹查找解决方案失败 - 指纹: {}, 错误: {}", fingerprint, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
            result.put("data", List.of());
        }

        return result;
    }
}
