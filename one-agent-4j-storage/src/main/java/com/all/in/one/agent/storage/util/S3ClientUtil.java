package com.all.in.one.agent.storage.util;

import com.all.in.one.agent.storage.dto.StorageConfigDTO;
import com.all.in.one.agent.storage.entity.StorageConfig;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3客户端工具类
 */
@Component
public class S3ClientUtil {
    
    /**
     * 根据配置创建S3客户端
     */
    public S3Client createS3Client(StorageConfig config) {
        return createS3Client(config.getEndpoint(), config.getAccessKeyId(), 
                config.getAccessKeySecret(), config.getRegion());
    }
    
    /**
     * 根据DTO创建S3客户端
     */
    public S3Client createS3Client(StorageConfigDTO configDTO) {
        return createS3Client(configDTO.getEndpoint(), configDTO.getAccessKeyId(), 
                configDTO.getAccessKeySecret(), configDTO.getRegion());
    }

    /**
     * 根据配置创建S3Presigner
     */
    public S3Presigner createS3Presigner(StorageConfig config) {
        return createS3Presigner(config.getEndpoint(), config.getAccessKeyId(),
                config.getAccessKeySecret(), config.getRegion());
    }

    /**
     * 根据DTO创建S3Presigner
     */
    public S3Presigner createS3Presigner(StorageConfigDTO configDTO) {
        return createS3Presigner(configDTO.getEndpoint(), configDTO.getAccessKeyId(),
                configDTO.getAccessKeySecret(), configDTO.getRegion());
    }

    /**
     * 创建S3客户端
     */
    private S3Client createS3Client(String endpoint, String accessKeyId, String accessKeySecret, String region) {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKeyId, accessKeySecret);
        
        // 创建S3Client构建器
        var builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build());
        
        // 如果提供了端点，使用自定义端点
        if (endpoint != null && !endpoint.trim().isEmpty()) {
            builder = builder.endpointOverride(URI.create(endpoint));
        }

        // 如果提供了区域，设置区域
        if (region != null && !region.trim().isEmpty()) {
            builder = builder.region(Region.of(region));
        }

        return builder.build();
    }

    /**
     * 创建S3Presigner
     */
    private S3Presigner createS3Presigner(String endpoint, String accessKeyId, String accessKeySecret, String region) {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKeyId, accessKeySecret);

        // 创建S3Presigner构建器
        var builder = S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials));

        // 如果提供了端点，使用自定义端点
        if (endpoint != null && !endpoint.trim().isEmpty()) {
            builder = builder.endpointOverride(URI.create(endpoint));
        }
        
        // 如果提供了区域，设置区域
        if (region != null && !region.trim().isEmpty()) {
            builder = builder.region(Region.of(region));
        }
        
        return builder.build();
    }
}
