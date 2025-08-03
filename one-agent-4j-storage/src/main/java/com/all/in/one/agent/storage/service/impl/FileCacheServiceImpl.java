package com.all.in.one.agent.storage.service.impl;

import com.all.in.one.agent.storage.config.StorageConfigProperties;
import com.all.in.one.agent.storage.service.FileCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 文件缓存服务实现类
 */
@Slf4j
@Service
public class FileCacheServiceImpl implements FileCacheService {
    
    private final StorageConfigProperties configProperties;
    private final Path cacheDir;
    private final ConcurrentHashMap<String, CacheEntry> cacheEntries = new ConcurrentHashMap<>();
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    
    public FileCacheServiceImpl(StorageConfigProperties configProperties) {
        this.configProperties = configProperties;
        this.cacheDir = Paths.get(configProperties.getCache().getCacheDir());
        initializeCache();
    }
    
    private void initializeCache() {
        try {
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }
            log.info("文件缓存目录初始化完成: {}", cacheDir);
        } catch (IOException e) {
            log.error("初始化缓存目录失败", e);
        }
    }
    
    @Override
    public void cacheFile(String cacheKey, InputStream inputStream, long fileSize) {
        if (!configProperties.getCache().isEnabled()) {
            return;
        }
        
        try {
            String fileName = generateCacheFileName(cacheKey);
            Path cacheFile = cacheDir.resolve(fileName);
            
            // 检查缓存大小限制
            if (cacheEntries.size() >= configProperties.getCache().getMaxEntries()) {
                evictOldestEntry();
            }
            
            // 写入缓存文件
            try (OutputStream outputStream = Files.newOutputStream(cacheFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            
            // 记录缓存条目
            CacheEntry entry = new CacheEntry(cacheKey, fileName, fileSize, System.currentTimeMillis());
            cacheEntries.put(cacheKey, entry);
            
            log.debug("文件已缓存: {} -> {}", cacheKey, fileName);
            
        } catch (IOException e) {
            log.error("缓存文件失败: {}", cacheKey, e);
        }
    }
    
    @Override
    public Optional<InputStream> getCachedFile(String cacheKey) {
        if (!configProperties.getCache().isEnabled()) {
            missCount.incrementAndGet();
            return Optional.empty();
        }
        
        CacheEntry entry = cacheEntries.get(cacheKey);
        if (entry == null) {
            missCount.incrementAndGet();
            return Optional.empty();
        }
        
        Path cacheFile = cacheDir.resolve(entry.getFileName());
        if (!Files.exists(cacheFile)) {
            cacheEntries.remove(cacheKey);
            missCount.incrementAndGet();
            return Optional.empty();
        }
        
        // 检查缓存是否过期
        long currentTime = System.currentTimeMillis();
        long expirationTime = configProperties.getCache().getExpiration() * 1000L;
        if (currentTime - entry.getTimestamp() > expirationTime) {
            cacheEntries.remove(cacheKey);
            try {
                Files.deleteIfExists(cacheFile);
            } catch (IOException e) {
                log.warn("删除过期缓存文件失败: {}", cacheFile, e);
            }
            missCount.incrementAndGet();
            return Optional.empty();
        }
        
        try {
            hitCount.incrementAndGet();
            return Optional.of(Files.newInputStream(cacheFile));
        } catch (IOException e) {
            log.error("读取缓存文件失败: {}", cacheKey, e);
            missCount.incrementAndGet();
            return Optional.empty();
        }
    }
    
    @Override
    public boolean isCached(String cacheKey) {
        if (!configProperties.getCache().isEnabled()) {
            return false;
        }
        
        CacheEntry entry = cacheEntries.get(cacheKey);
        if (entry == null) {
            return false;
        }
        
        Path cacheFile = cacheDir.resolve(entry.getFileName());
        return Files.exists(cacheFile);
    }
    
    @Override
    public void removeCache(String cacheKey) {
        CacheEntry entry = cacheEntries.remove(cacheKey);
        if (entry != null) {
            try {
                Files.deleteIfExists(cacheDir.resolve(entry.getFileName()));
                log.debug("缓存文件已删除: {}", cacheKey);
            } catch (IOException e) {
                log.warn("删除缓存文件失败: {}", cacheKey, e);
            }
        }
    }
    
    @Override
    public void clearCache() {
        cacheEntries.clear();
        try {
            Files.walk(cacheDir)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            log.warn("删除缓存文件失败: {}", file, e);
                        }
                    });
            log.info("缓存已清空");
        } catch (IOException e) {
            log.error("清空缓存失败", e);
        }
    }
    
    @Override
    public CacheStats getCacheStats() {
        long totalSize = cacheEntries.values().stream()
                .mapToLong(CacheEntry::getFileSize)
                .sum();
        
        return new CacheStats(
                cacheEntries.size(),
                totalSize,
                hitCount.get(),
                missCount.get()
        );
    }
    
    private String generateCacheFileName(String cacheKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(cacheKey.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return cacheKey.replaceAll("[^a-zA-Z0-9]", "_");
        }
    }
    
    private void evictOldestEntry() {
        CacheEntry oldestEntry = cacheEntries.values().stream()
                .min((e1, e2) -> Long.compare(e1.getTimestamp(), e2.getTimestamp()))
                .orElse(null);
        
        if (oldestEntry != null) {
            removeCache(oldestEntry.getCacheKey());
        }
    }
    
    private static class CacheEntry {
        private final String cacheKey;
        private final String fileName;
        private final long fileSize;
        private final long timestamp;
        
        public CacheEntry(String cacheKey, String fileName, long fileSize, long timestamp) {
            this.cacheKey = cacheKey;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.timestamp = timestamp;
        }
        
        public String getCacheKey() { return cacheKey; }
        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
        public long getTimestamp() { return timestamp; }
    }
} 