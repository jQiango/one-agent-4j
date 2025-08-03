package com.all.in.one.agent.storage.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 存储配置DTO
 */
@Data
public class StorageConfigDTO {
    
    private Long id;
    
    @NotBlank(message = "配置名称不能为空")
    private String name;
    
    @NotBlank(message = "存储类型不能为空")
    private String type;
    
    @NotBlank(message = "端点URL不能为空")
    private String endpoint;
    
    @NotBlank(message = "访问密钥ID不能为空")
    private String accessKeyId;
    
    @NotBlank(message = "访问密钥不能为空")
    private String accessKeySecret;
    
    private String region;
    
    private String defaultBucket;
    
    @NotNull(message = "是否启用不能为空")
    private Boolean enabled;
} 