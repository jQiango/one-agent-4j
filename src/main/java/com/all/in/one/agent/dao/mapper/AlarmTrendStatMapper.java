package com.all.in.one.agent.dao.mapper;

import com.all.in.one.agent.dao.entity.AlarmTrendStat;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 异常趋势统计 Mapper
 * <p>
 * 提供趋势统计数据的查询和聚合功能
 * </p>
 *
 * @author One Agent 4J
 */
@Mapper
public interface AlarmTrendStatMapper extends BaseMapper<AlarmTrendStat> {

    /**
     * 查询指定日期范围的统计数据
     *
     * @param startDate   开始日期
     * @param endDate     结束日期
     * @param serviceName 服务名称（可选）
     * @return 统计数据列表
     */
    List<AlarmTrendStat> selectByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("serviceName") String serviceName
    );

    /**
     * 查询指定服务的小时统计数据
     *
     * @param date        统计日期
     * @param serviceName 服务名称（可选）
     * @return 小时统计数据列表
     */
    List<AlarmTrendStat> selectHourlyStats(
            @Param("date") LocalDate date,
            @Param("serviceName") String serviceName
    );

    /**
     * 聚合统计：按天统计所有异常
     * <p>
     * 从 app_alarm_record 表聚合数据到 alarm_trend_stat 表
     * </p>
     *
     * @param date 统计日期
     */
    void aggregateDailyStat(@Param("date") LocalDate date);

    /**
     * 聚合统计：按小时统计
     * <p>
     * 从 app_alarm_record 表聚合数据到 alarm_trend_stat 表
     * </p>
     *
     * @param date 统计日期
     */
    void aggregateHourlyStat(@Param("date") LocalDate date);

    /**
     * 查询所有有统计数据的服务名称列表（去重）
     * <p>
     * 用于定时任务批量分析所有服务的趋势
     * </p>
     *
     * @return 服务名称列表
     */
    List<String> selectDistinctServices();
}
