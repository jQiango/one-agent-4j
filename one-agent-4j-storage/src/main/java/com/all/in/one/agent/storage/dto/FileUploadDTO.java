package com.all.in.one.agent.storage.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 文件上传DTO
 */
@Data
public class FileUploadDTO {
    
    @NotNull(message = "存储配置ID不能为空")
    private Long configId;
    
    private String bucketName;
    
    private String objectKey;
    
    private String fileName;
} 