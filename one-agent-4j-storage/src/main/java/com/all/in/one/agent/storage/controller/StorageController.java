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
     * 根��ID获取存储配置
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
            return Result.error("���件上传失败: " + e.getMessage());
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
            // 这里可以实现生成临时下载链接或压缩包的逻辑
            return Result.success("batch-download-url");
        } catch (Exception e) {
            log.error("批量下载文件失败", e);
            return Result.error("批量下载文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件夹大小统计
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
            listDTO.setMaxKeys(1000); // 设置一个较大的值来获取所有文件

            Map<String, Object> result = storageService.listFiles(listDTO);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) result.get("objects");

            long totalSize = files.stream()
                .mapToLong(file -> (Long) file.get("size"))
                .sum();
            int fileCount = files.size();

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
     * 搜索文件
     */
    @GetMapping("/search")
    public Result<Map<String, Object>> searchFiles(@RequestParam Long configId,
                                                   @RequestParam String bucketName,
                                                   @RequestParam String keyword,
                                                   @RequestParam(required = false) String prefix,
                                                   @RequestParam(defaultValue = "50") int maxResults) {
        try {
            // 获取文件列表
            FileListDTO listDTO = new FileListDTO();
            listDTO.setConfigId(configId);
            listDTO.setBucketName(bucketName);
            listDTO.setPrefix(prefix);
            listDTO.setMaxKeys(1000);

            Map<String, Object> result = storageService.listFiles(listDTO);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allFiles = (List<Map<String, Object>>) result.get("objects");

            // 根据关键词过滤文件
            List<Map<String, Object>> filteredFiles = allFiles.stream()
                .filter(file -> {
                    String key = (String) file.get("key");
                    return key.toLowerCase().contains(keyword.toLowerCase());
                })
                .limit(maxResults)
                .collect(Collectors.toList());

            Map<String, Object> searchResult = new HashMap<>();
            searchResult.put("files", filteredFiles);
            searchResult.put("totalFound", filteredFiles.size());
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
