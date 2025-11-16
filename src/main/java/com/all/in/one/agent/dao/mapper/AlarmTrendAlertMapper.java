package com.all.in.one.agent.dao.mapper;

import com.all.in.one.agent.dao.entity.AlarmTrendAlert;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 趋势预警记录 Mapper
 * <p>
 * 提供预警记录的查询和管理功能
 * </p>
 *
 * @author One Agent 4J
 */
@Mapper
public interface AlarmTrendAlertMapper extends BaseMapper<AlarmTrendAlert> {

    /**
     * 查询待处理的预警
     *
     * @return 待处理预警列表
     */
    List<AlarmTrendAlert> selectPendingAlerts();

    /**
     * 查询最近的预警记录
     *
     * @param hours 最近N小时
     * @param limit 返回数量限制
     * @return 预警记录列表
     */
    List<AlarmTrendAlert> selectRecentAlerts(
            @Param("hours") int hours,
            @Param("limit") int limit
    );
}
