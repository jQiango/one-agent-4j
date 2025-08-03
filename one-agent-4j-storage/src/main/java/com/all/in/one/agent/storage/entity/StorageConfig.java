package com.all.in.one.agent.storage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 存储配置实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("storage_config")
public class StorageConfig {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 配置名称
     */
    private String name;
    
    /**
     * 存储类型：S3, MINIO, OSS等
     */
    private String type;
    
    /**
     * 端点URL
     */
    private String endpoint;
    
    /**
     * 访问密钥ID
     */
    private String accessKeyId;
    
    /**
     * 访问密钥
     */
    private String accessKeySecret;
    
    /**
     * 区域
     */
    private String region;
    
    /**
     * 默认桶名
     */
    private String defaultBucket;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
} 