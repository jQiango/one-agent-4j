package com.all.in.one.agent.storage.controller;

import com.all.in.one.agent.common.result.Result;
import com.all.in.one.agent.storage.dto.FileListDTO;
import com.all.in.one.agent.storage.dto.FileUploadDTO;
import com.all.in.one.agent.storage.dto.StorageConfigDTO;
import com.all.in.one.agent.storage.entity.StorageConfig;
import com.all.in.one.agent.storage.entity.StorageFile;
import com.all.in.one.agent.storage.service.StorageService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 存储控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/storage")
@CrossOrigin(origins = "*")
public class StorageController {
    
    private final StorageService storageService;
    
    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }
    
    /**
     * 保存存储配置
     */
    @PostMapping("/config")
    public Result<StorageConfig> saveConfig(@Valid @RequestBody StorageConfigDTO configDTO) {
        try {
            StorageConfig config = storageService.saveConfig(configDTO);
            return Result.success(config);
        } catch (Exception e) {
            log.error("保存存储配置失败", e);
            return Result.error("保存存储配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取存储配置列表
     */
    @GetMapping("/config/list")
    public Result<List<StorageConfig>> getConfigList() {
        try {
            List<StorageConfig> configs = storageService.getConfigList();
            return Result.success(configs);
        } catch (Exception e) {
            log.error("获取存储配置列表失败", e);
            return Result.error("获取存储配置列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID获取存储配置
     */
    @GetMapping("/config/{id}")
    public Result<StorageConfig> getConfigById(@PathVariable Long id) {
        try {
            StorageConfig config = storageService.getConfigById(id);
            return Result.success(config);
        } catch (Exception e) {
            log.error("获取存储配置失败", e);
            return Result.error("获取存储配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除存储配置
     */
    @DeleteMapping("/config/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        try {
            storageService.deleteConfig(id);
            return Result.success();
        } catch (Exception e) {
            log.error("删除存储配置失败", e);
            return Result.error("删除存储配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试存储配置连接
     */
    @PostMapping("/config/test")
    public Result<Boolean> testConnection(@Valid @RequestBody StorageConfigDTO configDTO) {
        try {
            boolean success = storageService.testConnection(configDTO);
            return Result.success(success);
        } catch (Exception e) {
            log.error("测试存储连接失败", e);
            return Result.error("测试存储连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public Result<StorageFile> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "configId") Long configId,
                                        @RequestParam(value = "bucketName", required = false) String bucketName,
                                        @RequestParam(value = "objectKey", required = false) String objectKey,
                                        @RequestParam(value = "fileName", required = false) String fileName) {
        try {
            FileUploadDTO uploadDTO = new FileUploadDTO();
            uploadDTO.setConfigId(configId);
            uploadDTO.setBucketName(bucketName);
            uploadDTO.setObjectKey(objectKey);
            uploadDTO.setFileName(fileName);
            
            StorageFile storageFile = storageService.uploadFile(file, uploadDTO);
            return Result.success(storageFile);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 下载文件
     */
    @GetMapping("/download")
    public void downloadFile(@RequestParam Long configId,
                           @RequestParam String bucketName,
                           @RequestParam String objectKey,
                           HttpServletResponse response) {
        try {
            storageService.downloadFileByKey(configId, bucketName, objectKey, response);
        } catch (Exception e) {
            log.error("文件下载失败", e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("文件下载失败: " + e.getMessage());
            } catch (Exception ex) {
                log.error("写入错误响应失败", ex);
            }
        }
    }

    /**
     * 预览文件（按对象键，inline）
     */
    @GetMapping("/preview")
    public void previewByKey(@RequestParam Long configId,
                             @RequestParam String bucketName,
                             @RequestParam String objectKey,
                             HttpServletResponse response) {
        try {
            storageService.previewByKey(configId, bucketName, objectKey, response);
        } catch (Exception e) {
            log.error("文件预览失败", e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("文件预览失败: " + e.getMessage());
            } catch (Exception ex) {
                log.error("写入错误响应失败", ex);
            }
        }
    }
    
    /**
     * 获取文件列表
     */
    @PostMapping("/files/list")
    public Result<Map<String, Object>> listFiles(@RequestBody FileListDTO listDTO) {
        try {
            Map<String, Object> result = storageService.listFiles(listDTO);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取文件列表失败", e);
            return Result.error("获取文件列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除文件
     */
    @DeleteMapping("/files")
    public Result<Void> deleteFile(@RequestParam Long configId,
                                 @RequestParam String bucketName,
                                 @RequestParam String objectKey) {
        try {
            storageService.deleteFileByKey(configId, bucketName, objectKey);
            return Result.success();
        } catch (Exception e) {
            log.error("删除文件失败", e);
            return Result.error("删除文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取文件信息
     */
    @GetMapping("/files/{fileId}")
    public Result<StorageFile> getFileInfo(@PathVariable Long fileId) {
        try {
            StorageFile file = storageService.getFileInfo(fileId);
            return Result.success(file);
        } catch (Exception e) {
            log.error("获取文件信息失败", e);
            return Result.error("获取文件信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取文件预览URL
     */
    @GetMapping("/files/{fileId}/preview")
    public Result<String> getPreviewUrl(@PathVariable Long fileId) {
        try {
            String previewUrl = storageService.getPreviewUrl(fileId);
            return Result.success(previewUrl);
        } catch (Exception e) {
            log.error("获取预览URL失败", e);
            return Result.error("获取预览URL失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建文件夹
     */
    @PostMapping("/folder")
    public Result<Void> createFolder(@RequestParam Long configId,
                                   @RequestParam(required = false) String bucketName,
                                   @RequestParam String folderPath) {
        try {
            storageService.createFolder(configId, bucketName, folderPath);
            return Result.success();
        } catch (Exception e) {
            log.error("创建文件夹失败", e);
            return Result.error("创建文件夹失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取存储桶列表
     */
    @GetMapping("/buckets/{configId}")
    public Result<List<String>> listBuckets(@PathVariable Long configId) {
        try {
            List<String> buckets = storageService.listBuckets(configId);
            return Result.success(buckets);
        } catch (Exception e) {
            log.error("获取存储桶列表失败", e);
            return Result.error("获取存储桶列表失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除文件
     */
    @DeleteMapping("/files/batch")
    public Result<Void> batchDeleteFiles(@RequestBody Map<String, Object> request) {
        try {
            Long configId = Long.valueOf(request.get("configId").toString());
            String bucketName = (String) request.get("bucketName");
            @SuppressWarnings("unchecked")
            List<String> objectKeys = (List<String>) request.get("objectKeys");

            for (String objectKey : objectKeys) {
                storageService.deleteFileByKey(configId, bucketName, objectKey);
            }
            return Result.success();
        } catch (Exception e) {
            log.error("批量删除文件失败", e);
            return Result.error("批量删除文件失败: " + e.getMessage());
        }
    }

    /**
     * 批量下载文件（生成压缩包）
     */
    @PostMapping("/files/batch-download")
    public Result<String> batchDownloadFiles(@RequestBody Map<String, Object> request) {
        try {
            // TODO: 实现生成临时下载链接或压缩包的逻辑
            return Result.success("batch-download-url");
        } catch (Exception e) {
            log.error("批量下载文件失败", e);
            return Result.error("批量下载文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件夹大小统计（遍历分页）
     */
    @GetMapping("/folder/size")
    public Result<Map<String, Object>> getFolderSize(@RequestParam Long configId,
                                                    @RequestParam String bucketName,
                                                    @RequestParam(required = false) String prefix) {
        try {
            FileListDTO listDTO = new FileListDTO();
            listDTO.setConfigId(configId);
            listDTO.setBucketName(bucketName);
            listDTO.setPrefix(prefix);
            listDTO.setPageSize(1000); // 提高单页容量以加快统计
            String continuationToken = null;

            long totalSize = 0L;
            int fileCount = 0;
            int safetyPages = 0;

            do {
                listDTO.setContinuationToken(continuationToken);
                Map<String, Object> page = storageService.listFiles(listDTO);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> files = (List<Map<String, Object>>) page.get("files");

                if (files != null) {
                    for (Map<String, Object> f : files) {
                        Object sizeObj = f.get("size");
                        if (sizeObj instanceof Number) {
                            totalSize += ((Number) sizeObj).longValue();
                            fileCount++;
                        }
                    }
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> pagination = (Map<String, Object>) page.get("pagination");
                boolean hasMore = pagination != null && Boolean.TRUE.equals(pagination.get("hasMore"));
                continuationToken = pagination != null ? (String) pagination.get("nextContinuationToken") : null;

                if (++safetyPages > 2000) break; // 安全阈值，避免极端情况无限循环
                if (!hasMore) break;
            } while (continuationToken != null);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalSize", totalSize);
            stats.put("fileCount", fileCount);
            stats.put("formattedSize", formatFileSize(totalSize));

            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取文件夹大小失败", e);
            return Result.error("获取文件夹大小失败: " + e.getMessage());
        }
    }

    /**
     * 搜索文件（遍历分页直到达到maxResults或无更多）
     */
    @GetMapping("/search")
    public Result<Map<String, Object>> searchFiles(@RequestParam Long configId,
                                                   @RequestParam String bucketName,
                                                   @RequestParam String keyword,
                                                   @RequestParam(required = false) String prefix,
                                                   @RequestParam(defaultValue = "50") int maxResults) {
        try {
            FileListDTO listDTO = new FileListDTO();
            listDTO.setConfigId(configId);
            listDTO.setBucketName(bucketName);
            listDTO.setPrefix(prefix);
            listDTO.setPageSize(500);

            String continuationToken = null;
            List<Map<String, Object>> matched = new ArrayList<>();
            int safetyPages = 0;

            do {
                listDTO.setContinuationToken(continuationToken);
                Map<String, Object> page = storageService.listFiles(listDTO);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> files = (List<Map<String, Object>>) page.get("files");
                if (files != null) {
                    for (Map<String, Object> file : files) {
                        String key = (String) file.get("key");
                        if (key != null && key.toLowerCase().contains(keyword.toLowerCase())) {
                            matched.add(file);
                            if (matched.size() >= maxResults) break;
                        }
                    }
                }

                if (matched.size() >= maxResults) break;

                @SuppressWarnings("unchecked")
                Map<String, Object> pagination = (Map<String, Object>) page.get("pagination");
                boolean hasMore = pagination != null && Boolean.TRUE.equals(pagination.get("hasMore"));
                continuationToken = pagination != null ? (String) pagination.get("nextContinuationToken") : null;

                if (++safetyPages > 2000) break;
                if (!hasMore) break;
            } while (continuationToken != null);

            Map<String, Object> searchResult = new HashMap<>();
            searchResult.put("files", matched.size() > maxResults ? matched.subList(0, maxResults) : matched);
            searchResult.put("totalFound", matched.size());
            searchResult.put("keyword", keyword);

            return Result.success(searchResult);
        } catch (Exception e) {
            log.error("搜索文件失败", e);
            return Result.error("搜索文件失败: " + e.getMessage());
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }
}