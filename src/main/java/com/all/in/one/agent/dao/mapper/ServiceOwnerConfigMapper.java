package com.all.in.one.agent.dao.mapper;

import com.all.in.one.agent.dao.entity.AlarmOwnerConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 告警责任人配置 Mapper
 *
 * @author One Agent 4J
 */
@Mapper
public interface AlarmOwnerConfigMapper extends BaseMapper<AlarmOwnerConfig> {

    /**
     * 根据服务名称查询责任人配置
     *
     * @param serviceName 服务名称
     * @return 责任人配置，如果不存在则返回null
     */
    AlarmOwnerConfig selectByServiceName(@Param("serviceName") String serviceName);
}
