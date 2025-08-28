package com.all.in.one.agent.storage.service.impl;


import com.all.in.one.agent.storage.dto.FileListDTO;
import com.all.in.one.agent.storage.dto.FileUploadDTO;
import com.all.in.one.agent.storage.dto.StorageConfigDTO;
import com.all.in.one.agent.storage.entity.StorageConfig;
import com.all.in.one.agent.storage.entity.StorageFile;
import com.all.in.one.agent.storage.mapper.StorageConfigMapper;
import com.all.in.one.agent.storage.mapper.StorageFileMapper;
import com.all.in.one.agent.storage.service.StorageService;
import com.all.in.one.agent.storage.util.S3ClientUtil;
import com.all.in.one.agent.storage.security.FileSecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 存储服务实现类
 */
@Slf4j
@Service
public class StorageServiceImpl extends ServiceImpl<StorageConfigMapper, StorageConfig> implements StorageService {
    
    private final StorageFileMapper storageFileMapper;
    private final S3ClientUtil s3ClientUtil;
    private final FileSecurityUtils fileSecurityUtils;

    public StorageServiceImpl(StorageFileMapper storageFileMapper, S3ClientUtil s3ClientUtil, FileSecurityUtils fileSecurityUtils) {
        this.storageFileMapper = storageFileMapper;
        this.s3ClientUtil = s3ClientUtil;
        this.fileSecurityUtils = fileSecurityUtils;
    }
    
    @Override
    public StorageConfig saveConfig(StorageConfigDTO configDTO) {
        StorageConfig config = new StorageConfig();
        BeanUtils.copyProperties(configDTO, config);
        
        if (config.getId() == null) {
            config.setCreateTime(LocalDateTime.now());
        }
        config.setUpdateTime(LocalDateTime.now());
        
        saveOrUpdate(config);
        return config;
    }
    
    @Override
    public List<StorageConfig> getConfigList() {
        LambdaQueryWrapper<StorageConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(StorageConfig::getCreateTime);
        return list(wrapper);
    }
    
    @Override
    public StorageConfig getConfigById(Long id) {
        return getById(id);
    }
    
    @Override
    public void deleteConfig(Long id) {
        removeById(id);
    }
    
    @Override
    public boolean testConnection(StorageConfigDTO configDTO) {
        try {
            S3Client s3Client = s3ClientUtil.createS3Client(configDTO);
            ListBucketsRequest request = ListBucketsRequest.builder().build();
            s3Client.listBuckets(request);
            return true;
        } catch (Exception e) {
            log.error("测试存储连接失败", e);
            return false;
        }
    }
    
    @Override
    public StorageFile uploadFile(MultipartFile file, FileUploadDTO uploadDTO) {
        StorageConfig config = getConfigById(uploadDTO.getConfigId());
        if (config == null) {
            throw new RuntimeException("存储配置不存在");
        }
        
        // 文件安全检查
        if (!fileSecurityUtils.isFileSecure(file.getOriginalFilename(), file.getSize())) {
            throw new RuntimeException("文件类型不安全或文件过大");
        }

        try {
            S3Client s3Client = s3ClientUtil.createS3Client(config);
            
            String bucketName = uploadDTO.getBucketName() != null ? uploadDTO.getBucketName() : config.getDefaultBucket();

            // 安全清理文件名
            String originalFilename = fileSecurityUtils.sanitizeFilename(file.getOriginalFilename());
            String objectKey = uploadDTO.getObjectKey() != null ? uploadDTO.getObjectKey() :
                    generateObjectKey(originalFilename);

            // 检查存储桶是否存在，如果不存在则创建
            try {
                HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.headBucket(headBucketRequest);
            } catch (NoSuchBucketException e) {
                // 存储桶不存在，创建它
                CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.createBucket(createBucketRequest);
                log.info("创建存储桶: {}", bucketName);
            }
            
            // 上传文件到S3
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(
                    file.getInputStream(), file.getSize()));
            
            // 保存文件记录
            StorageFile storageFile = new StorageFile();
            storageFile.setFileName(uploadDTO.getFileName() != null ? uploadDTO.getFileName() : file.getOriginalFilename());
            storageFile.setFilePath(objectKey);
            storageFile.setFileSize(file.getSize());
            storageFile.setFileType(file.getContentType());
            storageFile.setBucketName(bucketName);
            storageFile.setObjectKey(objectKey);
            storageFile.setConfigId(config.getId());
            storageFile.setUploadTime(LocalDateTime.now());
            storageFile.setDeleted(false);
            
            storageFileMapper.insert(storageFile);
            return storageFile;
            
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }
    
    @Override
    public void downloadFile(Long fileId, HttpServletResponse response) {
        StorageFile storageFile = getFileInfo(fileId);
        if (storageFile == null) {
            throw new RuntimeException("文件不存在");
        }
        
        StorageConfig config = getConfigById(storageFile.getConfigId());
        if (config == null) {
            throw new RuntimeException("存储配置不存在");
        }
        
        try {
            S3Client s3Client = s3ClientUtil.createS3Client(config);
            
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(storageFile.getBucketName())
                    .key(storageFile.getObjectKey())
                    .build();
            
            // 设置响应头
            response.setContentType(storageFile.getFileType());
            response.setHeader("Content-Disposition", "attachment; filename=" + 
                    URLEncoder.encode(storageFile.getFileName(), StandardCharsets.UTF_8));
            
            // 复制文件流到响应
            s3Client.getObject(getObjectRequest, ResponseTransformer.toOutputStream(response.getOutputStream()));

            // 更新最后访问时间
            storageFile.setLastAccessTime(LocalDateTime.now());
            storageFileMapper.updateById(storageFile);
            
        } catch (Exception e) {
            log.error("文件下载失败", e);
            throw new RuntimeException("文件下载失败: " + e.getMessage());
        }
    }
    
    @Override
    public void downloadFileByKey(Long configId, String bucketName, String objectKey, HttpServletResponse response) {
        StorageConfig config = getConfigById(configId);
        if (config == null) {
            throw new RuntimeException("存储配置不存在");
        }
        
        try {
            S3Client s3Client = s3ClientUtil.createS3Client(config);
            
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            
            // 从对象键中提取文件名
            String fileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
            
            // 设置响应头
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=" + 
                    URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            
            // 复制文件流到响应
            s3Client.getObject(getObjectRequest, ResponseTransformer.toOutputStream(response.getOutputStream()));

        } catch (Exception e) {
            log.error("文件下载失败", e);
            throw new RuntimeException("文件下载失败: " + e.getMessage());
        }
    }
    
    @Override
    public Map<String, Object> listFiles(FileListDTO listDTO) {
        if (listDTO == null) {
            throw new RuntimeException("请求参数不能为空");
        }
        
        if (listDTO.getConfigId() == null) {
            throw new RuntimeException("存储配置ID不能为空");
        }
        
        StorageConfig config = getConfigById(listDTO.getConfigId());
        if (config == null) {
            throw new RuntimeException("存储配置不存在");
        }
        
        try {
            S3Client s3Client = s3ClientUtil.createS3Client(config);
            String bucketName = listDTO.getBucketName() != null ? listDTO.getBucketName() : config.getDefaultBucket();
            
            log.debug("开始获取文件列表 - bucket: {}, prefix: {}, delimiter: {}", 
                    bucketName, listDTO.getPrefix(), listDTO.getDelimiter());
            
            var requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(listDTO.getPageSize() != null ? listDTO.getPageSize() : listDTO.getMaxKeys());

            if (listDTO.getPrefix() != null && !listDTO.getPrefix().isEmpty()) {
                requestBuilder = requestBuilder.prefix(listDTO.getPrefix());
                log.debug("设置前缀: {}", listDTO.getPrefix());
            }
            
            if (listDTO.getDelimiter() != null && !listDTO.getDelimiter().isEmpty()) {
                requestBuilder = requestBuilder.delimiter(listDTO.getDelimiter());
                log.debug("设置分隔符: {}", listDTO.getDelimiter());
            }
            
            if (listDTO.getContinuationToken() != null && !listDTO.getContinuationToken().isEmpty()) {
                requestBuilder = requestBuilder.continuationToken(listDTO.getContinuationToken());
                log.debug("设置继续标记: {}", listDTO.getContinuationToken());
            }
            
            ListObjectsV2Request request = requestBuilder.build();
            log.debug("S3请求参数: {}", request);
            
            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            
            log.debug("S3响应 - 文件数量: {}, 文件夹数量: {}, 是否截断: {}", 
                    response.contents().size(), 
                    response.commonPrefixes() != null ? response.commonPrefixes().size() : 0,
                    response.isTruncated());
            
            Map<String, Object> result = new HashMap<>();

            // 1. 提取所有一级文件夹和当前目录下的文件
            List<Map<String, Object>> allFolders = new ArrayList<>();
            List<Map<String, Object>> allFiles = new ArrayList<>();

            // 先用原有逻辑提取一级文件夹
            if (response.commonPrefixes() != null) {
                for (CommonPrefix commonPrefix : response.commonPrefixes()) {
                    Map<String, Object> folderInfo = new HashMap<>();
                    String prefix = commonPrefix.prefix();
                    String folderName = prefix;
                    if (listDTO.getPrefix() != null && !listDTO.getPrefix().isEmpty()) {
                        folderName = prefix.replace(listDTO.getPrefix(), "");
                    }
                    if (folderName.endsWith("/")) {
                        folderName = folderName.substring(0, folderName.length() - 1);
                    }
                    if (!folderName.isEmpty()) {
                        folderInfo.put("key", prefix);
                        folderInfo.put("name", folderName);
                        folderInfo.put("isFolder", true);
                        folderInfo.put("lastModified", null);
                        folderInfo.put("size", 0L);
                        allFolders.add(folderInfo);
                    }
                }
            }
            // 补充：包含空目录占位对象（key 以 '/' 结尾且直接位于当前前缀下）
            for (S3Object obj : response.contents()) {
                String key = obj.key();
                if (key.endsWith("/")) {
                    String relative = (listDTO.getPrefix() != null && !listDTO.getPrefix().isEmpty())
                            ? (key.startsWith(listDTO.getPrefix()) ? key.substring(listDTO.getPrefix().length()) : null)
                            : key;
                    if (relative != null && !relative.isEmpty() && !relative.contains("/")) {
                        boolean exists = allFolders.stream().anyMatch(f -> key.equals(f.get("key")));
                        if (!exists) {
                            Map<String, Object> folderInfo = new HashMap<>();
                            folderInfo.put("key", key);
                            folderInfo.put("name", relative.substring(0, relative.length() - 1));
                            folderInfo.put("isFolder", true);
                            folderInfo.put("lastModified", null);
                            folderInfo.put("size", 0L);
                            allFolders.add(folderInfo);
                        }
                    }
                }
            }
            // 如果没有commonPrefixes，尝试从文件列表中提取
            if (allFolders.isEmpty()) {
                Set<String> folderSet = new HashSet<>();
                for (S3Object obj : response.contents()) {
                    String key = obj.key();
                    if (key.contains("/")) {
                        String folderPath;
                        if (listDTO.getPrefix() == null || listDTO.getPrefix().isEmpty()) {
                            folderPath = key.substring(0, key.indexOf("/") + 1);
                        } else {
                            if (key.startsWith(listDTO.getPrefix())) {
                                String relativePath = key.substring(listDTO.getPrefix().length());
                                if (relativePath.contains("/")) {
                                    String subFolder = relativePath.substring(0, relativePath.indexOf("/") + 1);
                                    folderPath = listDTO.getPrefix() + subFolder;
                                } else {
                                    continue;
                                }
                            } else {
                                continue;
                            }
                        }
                        folderSet.add(folderPath);
                    }
                }
                for (String folderPath : folderSet) {
                    String folderName = folderPath;
                    if (listDTO.getPrefix() != null && !listDTO.getPrefix().isEmpty()) {
                        folderName = folderPath.replace(listDTO.getPrefix(), "");
                    }
                    if (folderName.endsWith("/")) {
                        folderName = folderName.substring(0, folderName.length() - 1);
                    }
                    if (!folderName.isEmpty()) {
                        Map<String, Object> folderInfo = new HashMap<>();
                        folderInfo.put("key", folderPath);
                        folderInfo.put("name", folderName);
                        folderInfo.put("isFolder", true);
                        folderInfo.put("lastModified", null);
                        folderInfo.put("size", 0L);
                        allFolders.add(folderInfo);
                    }
                }
            }

            // 提取当前目录下的文件
            for (S3Object obj : response.contents()) {
                String key = obj.key();
                if (key.endsWith("/")) continue;
                if (listDTO.getPrefix() != null && key.equals(listDTO.getPrefix())) continue;
                if (listDTO.getPrefix() != null && !listDTO.getPrefix().isEmpty()) {
                    String relativePath = key.substring(listDTO.getPrefix().length());
                    if (relativePath.contains("/")) continue;
                }
                Map<String, Object> fileInfo = convertToFileInfo(obj);
                fileInfo.put("isFolder", false);
                allFiles.add(fileInfo);
            }

            // 2. 使用S3原生分页返回当前页（不做内存二次分页）
            List<Map<String, Object>> pageFolders = allFolders;
            List<Map<String, Object>> pageFiles = allFiles;

            Map<String, Object> pagination = new HashMap<>();
            int pageSize = listDTO.getPageSize() != null ? listDTO.getPageSize() : (listDTO.getMaxKeys() != null ? listDTO.getMaxKeys() : 100);
            pagination.put("pageSize", pageSize);
            pagination.put("hasMore", response.isTruncated());
            pagination.put("nextContinuationToken", response.nextContinuationToken());
            pagination.put("currentCount", pageFolders.size() + pageFiles.size());
            pagination.put("folderCount", pageFolders.size());
            pagination.put("fileCount", pageFiles.size());

            result.put("folders", pageFolders);
            result.put("files", pageFiles);
            result.put("pagination", pagination);
            return result;
            
        } catch (Exception e) {
            log.error("获取文件列表失败 - configId: {}, bucketName: {}, prefix: {}", 
                    listDTO.getConfigId(), listDTO.getBucketName(), listDTO.getPrefix(), e);
            throw new RuntimeException("获取文件列表失败: " + e.getMessage());
        }
    }
    
    @Override
    public void deleteFile(Long fileId) {
        StorageFile storageFile = getFileInfo(fileId);
        if (storageFile == null) {
            throw new RuntimeException("文件不存在");
        }
        
        StorageConfig config = getConfigById(storageFile.getConfigId());
        if (config == null) {
            throw new RuntimeException("存储配置不存在");
        }
        
        try {
            S3Client s3Client = s3ClientUtil.createS3Client(config);
            
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(storageFile.getBucketName())
                    .key(storageFile.getObjectKey())
                    .build();
            
            s3Client.deleteObject(deleteObjectRequest);
            
            // 标记文件��删除状态
            storageFile.setDeleted(true);
            storageFileMapper.updateById(storageFile);
            
        } catch (Exception e) {
            log.error("文件删除失败", e);
            throw new RuntimeException("文件删除失败: " + e.getMessage());
        }
    }
    
    @Override
    public void deleteFileByKey(Long configId, String bucketName, String objectKey) {
        StorageConfig config = getConfigById(configId);
        if (config == null) {
            throw new RuntimeException("存储配置不存在");
        }
        
        try {
            S3Client s3Client = s3ClientUtil.createS3Client(config);
            
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            
            s3Client.deleteObject(deleteObjectRequest);
            
        } catch (Exception e) {
            log.error("文件删除失败", e);
            throw new RuntimeException("文件删除失败: " + e.getMessage());
        }
    }
    
    @Override
    public StorageFile getFileInfo(Long fileId) {
        return storageFileMapper.selectById(fileId);
    }
    
    @Override
    public String getPreviewUrl(Long fileId) {
        StorageFile storageFile = getFileInfo(fileId);
        if (storageFile == null) {
            throw new RuntimeException("文件不存在");
        }
        
        StorageConfig config = getConfigById(storageFile.getConfigId());
        if (config == null) {
            throw new RuntimeException("存储配置不存在");
        }
        
        try {
            // 使用S3Presigner生成预签名URL
            S3Presigner presigner = s3ClientUtil.createS3Presigner(config);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(storageFile.getBucketName())
                    .key(storageFile.getObjectKey())
                    .build();
            
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(java.time.Duration.ofHours(1))
                    .getObjectRequest(getObjectRequest)
                    .build();

            return presigner.presignGetObject(presignRequest).url().toString();

        } catch (Exception e) {
            log.error("获取预览URL失败", e);
            throw new RuntimeException("��取预览URL失败: " + e.getMessage());
        }
    }
    
    @Override
    public void previewByKey(Long configId, String bucketName, String objectKey, HttpServletResponse response) {
        StorageConfig config = getConfigById(configId);
        if (config == null) {
            throw new RuntimeException("存储配置不存在");
        }

        try {
            S3Client s3Client = s3ClientUtil.createS3Client(config);

            // 获取内容类型
            String contentType = "application/octet-stream";
            try {
                HeadObjectRequest headReq = HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build();
                HeadObjectResponse headResp = s3Client.headObject(headReq);
                if (headResp.contentType() != null && !headResp.contentType().isEmpty()) {
                    contentType = headResp.contentType();
                }
            } catch (S3Exception ignore) {
                // 忽略HEAD失败，按默认类型预览
            }

            String fileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);

            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "inline; filename=" +
                    URLEncoder.encode(fileName, StandardCharsets.UTF_8));

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3Client.getObject(getObjectRequest, ResponseTransformer.toOutputStream(response.getOutputStream()));
        } catch (Exception e) {
            log.error("文件预览失败", e);
            throw new RuntimeException("文件预览失败: " + e.getMessage());
        }
    }

    @Override
    public void createFolder(Long configId, String bucketName, String folderPath) {
        StorageConfig config = getConfigById(configId);
        if (config == null) {
            throw new RuntimeException("存储配置不存在");
        }
        
        try {
            S3Client s3Client = s3ClientUtil.createS3Client(config);
            String actualBucketName = bucketName != null ? bucketName : config.getDefaultBucket();
            
            // 确保文件夹路径以/结尾
            if (!folderPath.endsWith("/")) {
                folderPath += "/";
            }
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(actualBucketName)
                    .key(folderPath)
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.empty());
            
        } catch (Exception e) {
            log.error("创建文件夹失败", e);
            throw new RuntimeException("创建文件夹失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<String> listBuckets(Long configId) {
        StorageConfig config = getConfigById(configId);
        if (config == null) {
            throw new RuntimeException("存���配置不存在");
        }
        
        try {
            S3Client s3Client = s3ClientUtil.createS3Client(config);
            ListBucketsRequest request = ListBucketsRequest.builder().build();
            ListBucketsResponse response = s3Client.listBuckets(request);
            
            return response.buckets().stream()
                    .map(Bucket::name)
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("获取存储桶列表失败", e);
            throw new RuntimeException("获取存储桶列表失败: " + e.getMessage());
        }
    }
    
    private String generateObjectKey(String originalFilename) {
        String extension = FilenameUtils.getExtension(originalFilename);
        String baseName = FilenameUtils.getBaseName(originalFilename);
        String timestamp = String.valueOf(System.currentTimeMillis());
        return baseName + "_" + timestamp + "." + extension;
    }
    
    private Map<String, Object> convertToFileInfo(S3Object s3Object) {
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("key", s3Object.key());
        fileInfo.put("size", s3Object.size());
        fileInfo.put("lastModified", s3Object.lastModified());
        fileInfo.put("storageClass", s3Object.storageClassAsString());
        return fileInfo;
    }
}
