package com.all.in.one.agent.api.controller;

import com.all.in.one.agent.model.TrendReport;
import com.all.in.one.agent.service.TrendAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 趋势分析 REST API
 * <p>
 * 提供异常趋势分析功能
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@RestController
@RequestMapping("/api/trend")
public class TrendAnalysisController {

    private final TrendAnalysisService trendAnalysisService;

    public TrendAnalysisController(TrendAnalysisService trendAnalysisService) {
        this.trendAnalysisService = trendAnalysisService;
        log.info("TrendAnalysisController 初始化完成");
    }

    /**
     * 获取服务的异常趋势分析
     *
     * @param serviceName 服务名称
     * @param days 统计天数，默认7天
     * @return 趋势报告
     */
    @GetMapping("/analyze/{serviceName}")
    public TrendReport analyzeTrend(
            @PathVariable String serviceName,
            @RequestParam(defaultValue = "7") int days) {

        log.info("查询趋势分析 - 服务: {}, 天数: {}", serviceName, days);

        if (days < 1 || days > 90) {
            log.warn("天数参数异常 - 服务: {}, 天数: {}, 使用默认值7", serviceName, days);
            days = 7;
        }

        return trendAnalysisService.analyzeTrend(serviceName, days);
    }
}
