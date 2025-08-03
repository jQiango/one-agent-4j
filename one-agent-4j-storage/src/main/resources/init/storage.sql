-- 存储配置表
CREATE TABLE IF NOT EXISTS `storage_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '配置名称',
  `type` varchar(20) NOT NULL COMMENT '存储类型：S3, MINIO, OSS等',
  `endpoint` varchar(255) NOT NULL COMMENT '端点URL',
  `access_key_id` varchar(100) NOT NULL COMMENT '访问密钥ID',
  `access_key_secret` varchar(255) NOT NULL COMMENT '访问密钥',
  `region` varchar(50) DEFAULT NULL COMMENT '区域',
  `default_bucket` varchar(100) DEFAULT NULL COMMENT '默认桶名',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='存储配置表';

-- 存储文件表
CREATE TABLE IF NOT EXISTS `storage_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_path` varchar(500) NOT NULL COMMENT '文件路径',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `file_type` varchar(100) DEFAULT NULL COMMENT '文件类型',
  `bucket_name` varchar(100) NOT NULL COMMENT '存储桶名',
  `object_key` varchar(500) NOT NULL COMMENT '对象键',
  `config_id` bigint NOT NULL COMMENT '存储配置ID',
  `md5` varchar(32) DEFAULT NULL COMMENT '文件MD5',
  `upload_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `last_access_time` datetime DEFAULT NULL COMMENT '最后访问时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_config_id` (`config_id`),
  KEY `idx_bucket_name` (`bucket_name`),
  KEY `idx_upload_time` (`upload_time`),
  KEY `idx_deleted` (`deleted`),
  CONSTRAINT `fk_storage_file_config` FOREIGN KEY (`config_id`) REFERENCES `storage_config` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='存储文件表';

-- 插入示例数据
INSERT INTO `storage_config` (`name`, `type`, `endpoint`, `access_key_id`, `access_key_secret`, `region`, `default_bucket`, `enabled`) VALUES
('MinIO本地存储', 'MINIO', 'http://localhost:9000', 'minioadmin', 'minioadmin', 'us-east-1', 'default', 1),
('AWS S3存储', 'S3', 'https://s3.amazonaws.com', 'your-access-key-id', 'your-secret-access-key', 'us-east-1', 'my-bucket', 0); 