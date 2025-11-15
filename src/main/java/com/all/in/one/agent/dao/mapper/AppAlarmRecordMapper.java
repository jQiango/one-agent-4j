package com.all.in.one.agent.dao.mapper;

import com.all.in.one.agent.dao.entity.AppAlarmRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警记录 Mapper
 *
 * @author One Agent 4J
 */
@Mapper
public interface AppAlarmRecordMapper extends BaseMapper<AppAlarmRecord> {

    /**
     * 查询最近 N 分钟内的告警记录
     *
     * @param appName 应用名称
     * @param startTime 开始时间
     * @param limit 最大返回数量
     * @return 告警记录列表
     */
    @Select("SELECT * FROM app_alarm_record " +
            "WHERE app_name = #{appName} " +
            "AND occurred_at >= #{startTime} " +
            "ORDER BY occurred_at DESC " +
            "LIMIT #{limit}")
    List<AppAlarmRecord> findRecentExceptions(
            @Param("appName") String appName,
            @Param("startTime") LocalDateTime startTime,
            @Param("limit") int limit
    );
}
