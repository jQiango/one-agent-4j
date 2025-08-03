package com.all.in.one.agent.storage.service.impl;

import com.all.in.one.agent.storage.mapper.StorageFileMapper;
import com.all.in.one.agent.storage.service.StorageStatsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 存储统计服务实现类
 */
@Service
public class StorageStatsServiceImpl implements StorageStatsService {

    private final StorageFileMapper storageFileMapper;

    public StorageStatsServiceImpl(StorageFileMapper storageFileMapper) {
        this.storageFileMapper = storageFileMapper;
    }

    @Override
    public Map<String, Object> getConfigStats(Long configId) {
        LambdaQueryWrapper<com.all.in.one.agent.storage.entity.StorageFile> wrapper =
            Wrappers.<com.all.in.one.agent.storage.entity.StorageFile>lambdaQuery()
                .eq(com.all.in.one.agent.storage.entity.StorageFile::getConfigId, configId)
                .eq(com.all.in.one.agent.storage.entity.StorageFile::getDeleted, false);

        var files = storageFileMapper.selectList(wrapper);

        long totalSize = files.stream().mapToLong(com.all.in.one.agent.storage.entity.StorageFile::getFileSize).sum();
        int totalFiles = files.size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFiles", totalFiles);
        stats.put("totalSize", totalSize);
        stats.put("formattedSize", formatFileSize(totalSize));
        stats.put("avgFileSize", totalFiles > 0 ? totalSize / totalFiles : 0);

        return stats;
    }

    @Override
    public Map<String, Object> getFileTypeStats(Long configId) {
        LambdaQueryWrapper<com.all.in.one.agent.storage.entity.StorageFile> wrapper =
            Wrappers.<com.all.in.one.agent.storage.entity.StorageFile>lambdaQuery()
                .eq(com.all.in.one.agent.storage.entity.StorageFile::getConfigId, configId)
                .eq(com.all.in.one.agent.storage.entity.StorageFile::getDeleted, false);

        var files = storageFileMapper.selectList(wrapper);

        Map<String, Long> typeStats = files.stream()
            .collect(Collectors.groupingBy(
                file -> getFileExtension(file.getFileName()),
                Collectors.counting()
            ));

        Map<String, Object> result = new HashMap<>();
        result.put("typeStats", typeStats);
        result.put("totalTypes", typeStats.size());

        return result;
    }

    @Override
    public Map<String, Object> getUploadTrendStats(Long configId, int days) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);

        LambdaQueryWrapper<com.all.in.one.agent.storage.entity.StorageFile> wrapper =
            Wrappers.<com.all.in.one.agent.storage.entity.StorageFile>lambdaQuery()
                .eq(com.all.in.one.agent.storage.entity.StorageFile::getConfigId, configId)
                .eq(com.all.in.one.agent.storage.entity.StorageFile::getDeleted, false)
                .between(com.all.in.one.agent.storage.entity.StorageFile::getUploadTime, startTime, endTime);

        var files = storageFileMapper.selectList(wrapper);

        Map<String, Long> dailyStats = files.stream()
            .collect(Collectors.groupingBy(
                file -> file.getUploadTime().toLocalDate().toString(),
                Collectors.counting()
            ));

        Map<String, Object> result = new HashMap<>();
        result.put("dailyStats", dailyStats);
        result.put("totalUploads", files.size());
        result.put("period", days + " days");

        return result;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }
}
