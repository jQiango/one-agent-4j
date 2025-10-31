package com.all.in.one.agent.dao.mapper;

import com.all.in.one.agent.dao.entity.ExceptionRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异常记录 Mapper
 *
 * @author One Agent 4J
 */
@Mapper
public interface ExceptionRecordMapper extends BaseMapper<ExceptionRecord> {

    /**
     * 查询最近 N 分钟内的异常记录
     *
     * @param appName 应用名称
     * @param startTime 开始时间
     * @param limit 最大返回数量
     * @return 异常记录列表
     */
    @Select("SELECT * FROM exception_record " +
            "WHERE app_name = #{appName} " +
            "AND occurred_at >= #{startTime} " +
            "ORDER BY occurred_at DESC " +
            "LIMIT #{limit}")
    List<ExceptionRecord> findRecentExceptions(
            @Param("appName") String appName,
            @Param("startTime") LocalDateTime startTime,
            @Param("limit") int limit
    );
}
