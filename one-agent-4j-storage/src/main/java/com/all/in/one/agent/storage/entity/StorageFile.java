package com.all.in.one.agent.storage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 存储文件实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("storage_file")
public class StorageFile {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件路径
     */
    private String filePath;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件类型
     */
    private String fileType;
    
    /**
     * 存储桶名
     */
    private String bucketName;
    
    /**
     * 对象键
     */
    private String objectKey;
    
    /**
     * 存储配置ID
     */
    private Long configId;
    
    /**
     * 文件MD5
     */
    private String md5;
    
    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;
    
    /**
     * 最后访问时间
     */
    private LocalDateTime lastAccessTime;
    
    /**
     * 是否删除
     */
    private Boolean deleted;
} 