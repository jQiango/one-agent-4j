package com.all.in.one.agent.storage.controller;

import com.all.in.one.agent.common.result.Result;
import com.all.in.one.agent.storage.service.StorageStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 存储统计控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/storage/stats")
@CrossOrigin(origins = "*")
public class StorageStatsController {

    private final StorageStatsService storageStatsService;

    public StorageStatsController(StorageStatsService storageStatsService) {
        this.storageStatsService = storageStatsService;
    }

    /**
     * 获取存储配置统计
     */
    @GetMapping("/configs/{configId}")
    public Result<Map<String, Object>> getConfigStats(@PathVariable Long configId) {
        try {
            Map<String, Object> stats = storageStatsService.getConfigStats(configId);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取存储配置统计失败", e);
            return Result.error("获取存储配置统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件类型统计
     */
    @GetMapping("/file-types/{configId}")
    public Result<Map<String, Object>> getFileTypeStats(@PathVariable Long configId) {
        try {
            Map<String, Object> stats = storageStatsService.getFileTypeStats(configId);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取文件类型统计失败", e);
            return Result.error("获取文件类型统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取上传趋势统计
     */
    @GetMapping("/upload-trend/{configId}")
    public Result<Map<String, Object>> getUploadTrendStats(@PathVariable Long configId,
                                                           @RequestParam(defaultValue = "7") int days) {
        try {
            Map<String, Object> stats = storageStatsService.getUploadTrendStats(configId, days);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取上传趋势统计失败", e);
            return Result.error("获取上传趋势统计失败: " + e.getMessage());
        }
    }
}
