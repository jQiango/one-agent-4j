package com.all.in.one.agent.storage.service;

import java.util.Map;

/**
 * 存储统计服务接口
 */
public interface StorageStatsService {

    /**
     * 获取存储配置统计
     */
    Map<String, Object> getConfigStats(Long configId);

    /**
     * 获取文件类型统计
     */
    Map<String, Object> getFileTypeStats(Long configId);

    /**
     * 获取上传趋势统计
     */
    Map<String, Object> getUploadTrendStats(Long configId, int days);
}
