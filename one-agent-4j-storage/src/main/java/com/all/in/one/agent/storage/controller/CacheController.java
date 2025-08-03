package com.all.in.one.agent.storage.controller;

import com.all.in.one.agent.common.result.Result;
import com.all.in.one.agent.storage.service.FileCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/storage/cache")
@CrossOrigin(origins = "*")
public class CacheController {
    
    private final FileCacheService fileCacheService;
    
    public CacheController(FileCacheService fileCacheService) {
        this.fileCacheService = fileCacheService;
    }
    
    /**
     * 获取缓存统计信息
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getCacheStats() {
        try {
            FileCacheService.CacheStats stats = fileCacheService.getCacheStats();
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalFiles", stats.getTotalFiles());
            result.put("totalSize", stats.getTotalSize());
            result.put("formattedTotalSize", stats.getFormattedTotalSize());
            result.put("hitCount", stats.getHitCount());
            result.put("missCount", stats.getMissCount());
            result.put("hitRate", String.format("%.2f%%", stats.getHitRate() * 100));
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取缓存统计信息失败", e);
            return Result.error("获取缓存统计信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 清空缓存
     */
    @DeleteMapping("/clear")
    public Result<Void> clearCache() {
        try {
            fileCacheService.clearCache();
            return Result.success();
        } catch (Exception e) {
            log.error("清空缓存失败", e);
            return Result.error("清空缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除指定缓存
     */
    @DeleteMapping("/{cacheKey}")
    public Result<Void> removeCache(@PathVariable String cacheKey) {
        try {
            fileCacheService.removeCache(cacheKey);
            return Result.success();
        } catch (Exception e) {
            log.error("删除缓存失败: {}", cacheKey, e);
            return Result.error("删除缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查文件是否已缓存
     */
    @GetMapping("/{cacheKey}/exists")
    public Result<Boolean> isCached(@PathVariable String cacheKey) {
        try {
            boolean isCached = fileCacheService.isCached(cacheKey);
            return Result.success(isCached);
        } catch (Exception e) {
            log.error("检查缓存状态失败: {}", cacheKey, e);
            return Result.error("检查缓存状态失败: " + e.getMessage());
        }
    }
} 