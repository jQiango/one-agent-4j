package com.all.in.one.agent.job;

import com.all.in.one.agent.dao.mapper.AlarmTrendStatMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 趋势统计数据聚合定时任务
 * <p>
 * 负责定期从 app_alarm_record 表聚合数据到 alarm_trend_stat 表
 * </p>
 *
 * @author One Agent 4J
 */
@Slf4j
@Component
public class TrendStatAggregationJob {

    private final AlarmTrendStatMapper trendStatMapper;

    public TrendStatAggregationJob(AlarmTrendStatMapper trendStatMapper) {
        this.trendStatMapper = trendStatMapper;
        log.info("TrendStatAggregationJob 初始化完成");
    }

    /**
     * 每小时执行一次，聚合上一小时的数据
     * <p>
     * 执行时间：每小时第5分钟
     * </p>
     */
    @Scheduled(cron = "0 5 * * * ?")
    public void aggregateHourlyData() {
        try {
            LocalDate today = LocalDate.now();
            log.info("开始执行小时统计聚合 - 日期: {}", today);

            long startTime = System.currentTimeMillis();
            trendStatMapper.aggregateHourlyStat(today);
            long duration = System.currentTimeMillis() - startTime;

            log.info("小时统计聚合完成 - 日期: {}, 耗时: {}ms", today, duration);

        } catch (Exception e) {
            log.error("小时统计聚合失败", e);
        }
    }

    /**
     * 每天凌晨1点执行，聚合昨天的数据
     * <p>
     * 执行时间：每天 01:00:00
     * </p>
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void aggregateDailyData() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            log.info("开始执行每日统计聚合 - 日期: {}", yesterday);

            long startTime = System.currentTimeMillis();
            trendStatMapper.aggregateDailyStat(yesterday);
            long duration = System.currentTimeMillis() - startTime;

            log.info("每日统计聚合完成 - 日期: {}, 耗时: {}ms", yesterday, duration);

        } catch (Exception e) {
            log.error("每日统计聚合失败", e);
        }
    }

    /**
     * 每周日凌晨2点执行，补偿聚合过去7天的数据
     * <p>
     * 用于修复可能遗漏的统计数据
     * 执行时间：每周日 02:00:00
     * </p>
     */
    @Scheduled(cron = "0 0 2 ? * SUN")
    public void compensateAggregation() {
        try {
            log.info("开始执行补偿聚合 - 过去7天");

            long startTime = System.currentTimeMillis();
            int aggregatedDays = 0;

            for (int i = 1; i <= 7; i++) {
                LocalDate date = LocalDate.now().minusDays(i);
                try {
                    trendStatMapper.aggregateDailyStat(date);
                    aggregatedDays++;
                } catch (Exception e) {
                    log.error("补偿聚合失败 - 日期: {}", date, e);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("补偿聚合完成 - 成功: {}天, 耗时: {}ms", aggregatedDays, duration);

        } catch (Exception e) {
            log.error("补偿聚合失败", e);
        }
    }
}
