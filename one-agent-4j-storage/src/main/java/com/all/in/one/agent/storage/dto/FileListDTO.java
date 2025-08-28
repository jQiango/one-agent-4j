package com.all.in.one.agent.storage.dto;

import lombok.Data;

/**
 * 文件列表查询DTO
 */
@Data
public class FileListDTO {
    
    private Long configId;
    
    private String bucketName;
    
    private String prefix;
    
    private String delimiter;
    
    // 默认每页返回数量（与前端对齐：100）
    private Integer maxKeys = 100;

    private String continuationToken;

    // 分页参数（与S3 maxKeys对齐）
    private Integer pageSize = 100;  // 每页显示数量

    private String nextMarker;      // 下一页标记（保留兼容字段，不使用）

    private Integer pageNo = 1;     // 逻辑页码（兼容保留，实际使用 continuationToken）
}