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
    
    private Integer maxKeys = 50;  // 降低默认每页数量，便于分页

    private String continuationToken;

    // 新增分页参数
    private Integer pageSize = 50;  // 每页显示数量

    private String nextMarker;      // 下一页标记

    private Integer pageNo = 1;
}
