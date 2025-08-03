package com.all.in.one.agent.storage.dto;

import lombok.Data;

/**
 * 文件上传进度DTO
 */
@Data
public class UploadProgressDTO {
    
    /**
     * 文件ID
     */
    private String fileId;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 已上传字节数
     */
    private Long uploadedBytes;
    
    /**
     * 上传进度百分比
     */
    private Integer progress;
    
    /**
     * 上传状态：UPLOADING, SUCCESS, FAILED
     */
    private String status;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 开始时间
     */
    private Long startTime;
    
    /**
     * 预计剩余时间（秒）
     */
    private Long estimatedTimeRemaining;
    
    /**
     * 上传速度（字节/秒）
     */
    private Long uploadSpeed;
    
    public UploadProgressDTO() {}
    
    public UploadProgressDTO(String fileId, String fileName, Long fileSize) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.uploadedBytes = 0L;
        this.progress = 0;
        this.status = "UPLOADING";
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * 更新上传进度
     */
    public void updateProgress(Long uploadedBytes) {
        this.uploadedBytes = uploadedBytes;
        if (fileSize > 0) {
            this.progress = (int) ((uploadedBytes * 100) / fileSize);
        }
        
        // 计算上传速度
        long currentTime = System.currentTimeMillis();
        long elapsedTime = (currentTime - startTime) / 1000; // 转换为秒
        if (elapsedTime > 0) {
            this.uploadSpeed = uploadedBytes / elapsedTime;
            
            // 计算预计剩余时间
            long remainingBytes = fileSize - uploadedBytes;
            if (uploadSpeed > 0) {
                this.estimatedTimeRemaining = remainingBytes / uploadSpeed;
            }
        }
    }
    
    /**
     * 标记上传成功
     */
    public void markSuccess() {
        this.status = "SUCCESS";
        this.progress = 100;
    }
    
    /**
     * 标记上传失败
     */
    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
    }
} 