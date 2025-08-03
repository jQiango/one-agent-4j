package com.all.in.one.agent.storage.service;

import java.io.InputStream;
import java.util.Optional;

/**
 * 文件缓存服务接口
 */
public interface FileCacheService {
    
    /**
     * 缓存文件
     */
    void cacheFile(String cacheKey, InputStream inputStream, long fileSize);
    
    /**
     * 获取缓存文件
     */
    Optional<InputStream> getCachedFile(String cacheKey);
    
    /**
     * 检查文件是否已缓存
     */
    boolean isCached(String cacheKey);
    
    /**
     * 删除缓存文件
     */
    void removeCache(String cacheKey);
    
    /**
     * 清空所有缓存
     */
    void clearCache();
    
    /**
     * 获取缓存统计信息
     */
    CacheStats getCacheStats();
    
    /**
     * 缓存统计信息
     */
    class CacheStats {
        private long totalFiles;
        private long totalSize;
        private long hitCount;
        private long missCount;
        
        public CacheStats(long totalFiles, long totalSize, long hitCount, long missCount) {
            this.totalFiles = totalFiles;
            this.totalSize = totalSize;
            this.hitCount = hitCount;
            this.missCount = missCount;
        }
        
        public long getTotalFiles() { return totalFiles; }
        public long getTotalSize() { return totalSize; }
        public long getHitCount() { return hitCount; }
        public long getMissCount() { return missCount; }
        
        public double getHitRate() {
            long totalRequests = hitCount + missCount;
            return totalRequests > 0 ? (double) hitCount / totalRequests : 0.0;
        }
        
        public String getFormattedTotalSize() {
            if (totalSize < 1024) return totalSize + " B";
            if (totalSize < 1024 * 1024) return String.format("%.1f KB", totalSize / 1024.0);
            if (totalSize < 1024 * 1024 * 1024) return String.format("%.1f MB", totalSize / (1024.0 * 1024));
            return String.format("%.1f GB", totalSize / (1024.0 * 1024 * 1024));
        }
    }
} 