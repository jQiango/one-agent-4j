package com.all.in.one.agent.storage.service;

import com.all.in.one.agent.storage.dto.FileListDTO;
import com.all.in.one.agent.storage.dto.FileUploadDTO;
import com.all.in.one.agent.storage.dto.StorageConfigDTO;
import com.all.in.one.agent.storage.entity.StorageConfig;
import com.all.in.one.agent.storage.entity.StorageFile;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

/**
 * 存储服务接口
 */
public interface StorageService {
    
    /**
     * 保存存储配置
     */
    StorageConfig saveConfig(StorageConfigDTO configDTO);
    
    /**
     * 获取存储配置列表
     */
    List<StorageConfig> getConfigList();
    
    /**
     * 根据ID获取存储配置
     */
    StorageConfig getConfigById(Long id);
    
    /**
     * 删除存储配置
     */
    void deleteConfig(Long id);
    
    /**
     * 测试存储配置连接
     */
    boolean testConnection(StorageConfigDTO configDTO);
    
    /**
     * 上传文件
     */
    StorageFile uploadFile(MultipartFile file, FileUploadDTO uploadDTO);
    
    /**
     * 下载文件
     */
    void downloadFile(Long fileId, HttpServletResponse response);
    
    /**
     * 通过对象键下载文件
     */
    void downloadFileByKey(Long configId, String bucketName, String objectKey, HttpServletResponse response);
    
    /**
     * 获取文件列表
     */
    Map<String, Object> listFiles(FileListDTO listDTO);
    
    /**
     * 删除文件
     */
    void deleteFile(Long fileId);
    
    /**
     * 通过对象键删除文件
     */
    void deleteFileByKey(Long configId, String bucketName, String objectKey);
    
    /**
     * 获取文件信息
     */
    StorageFile getFileInfo(Long fileId);
    
    /**
     * 获取文件预览URL
     */
    String getPreviewUrl(Long fileId);
    
    /**
     * 按对象键直接预览（流式输出，Content-Disposition=inline）
     */
    void previewByKey(Long configId, String bucketName, String objectKey, HttpServletResponse response);
    
    /**
     * 创建文件夹
     */
    void createFolder(Long configId, String bucketName, String folderPath);
    
    /**
     * 获取存储桶列表
     */
    List<String> listBuckets(Long configId);
}