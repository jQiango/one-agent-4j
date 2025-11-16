package com.all.in.one.agent.dao.mapper;

import com.all.in.one.agent.dao.entity.AlarmSolutionKb;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 告警解决方案知识库 Mapper
 *
 * @author One Agent 4J
 */
@Mapper
public interface AlarmSolutionKbMapper extends BaseMapper<AlarmSolutionKb> {

    /**
     * 根据异常指纹查询解决方案（按评分排序）
     *
     * @param fingerprint 异常指纹
     * @return 解决方案列表
     */
    List<AlarmSolutionKb> selectByFingerprint(@Param("fingerprint") String fingerprint);

    /**
     * 查询高评分方案（TOP N）
     *
     * @param limit 返回数量
     * @return 高评分方案列表
     */
    List<AlarmSolutionKb> selectTopRatedSolutions(@Param("limit") int limit);

    /**
     * 根据服务名称和异常类型查询相似方案
     *
     * @param serviceName   服务名称
     * @param exceptionType 异常类型
     * @param limit         返回数量
     * @return 相似方案列表
     */
    List<AlarmSolutionKb> selectSimilarSolutions(
            @Param("serviceName") String serviceName,
            @Param("exceptionType") String exceptionType,
            @Param("limit") int limit
    );
}
