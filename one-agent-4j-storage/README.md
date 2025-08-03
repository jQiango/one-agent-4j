# 对象存储模块 (one-agent-4j-storage)

## 功能特性

- 支持多种S3兼容的对象存储服务
  - AWS S3
  - MinIO
  - 阿里云OSS
  - 其他S3兼容服务
- 文件上传下载
- 文件目录浏览
- 文件夹创建
- 文件删除
- 文件预览
- 存储配置管理
- 现代化的Vue前端界面
- **文件缓存管理**
- **上传进度监控**
- **文件类型检测**
- **全局异常处理**
- **存储统计功能**

## 技术栈

### 后端
- Spring Boot 3.4.8
- MyBatis-Plus 3.5.12
- AWS S3 SDK 2.28.16
- MinIO Client 8.5.7
- MySQL 数据库

### 前端
- Vue 3.2.31
- Bootstrap 5.1.3
- Axios 0.27.2

## 快速开始

### 1. 环境准备

- JDK 17+
- MySQL 8.0+
- Maven 3.6+

### 2. 数据库配置

执行 `src/main/resources/init/storage.sql` 创建数据库表。

### 3. 配置文件

修改 `src/main/resources/application-storage.yml` 中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/one_agent_4j?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

### 4. 启动应用

#### Windows
```bash
start.bat
```

#### Linux/Mac
```bash
./start.sh
```

#### 手动启动
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=storage
```

应用将在 `http://localhost:8081` 启动。

### 5. 访问前端界面

打开浏览器访问：`http://localhost:8081/storage-ui/`

## API 接口

### 存储配置管理

- `POST /api/storage/config` - 保存存储配置
- `GET /api/storage/config/list` - 获取配置列表
- `GET /api/storage/config/{id}` - 获取配置详情
- `DELETE /api/storage/config/{id}` - 删除配置
- `POST /api/storage/config/test` - 测试连接

### 文件管理

- `POST /api/storage/upload` - 上传文件
- `GET /api/storage/download/{fileId}` - 下载文件
- `POST /api/storage/files/list` - 获取文件列表
- `DELETE /api/storage/files/{fileId}` - 删除文件
- `GET /api/storage/files/{fileId}` - 获取文件信息
- `GET /api/storage/files/{fileId}/preview` - 获取预览URL
- `POST /api/storage/folder` - 创建文件夹
- `GET /api/storage/buckets/{configId}` - 获取存储桶列表

### 缓存管理

- `GET /api/storage/cache/stats` - 获取缓存统计信息
- `DELETE /api/storage/cache/clear` - 清空缓存
- `DELETE /api/storage/cache/{cacheKey}` - 删除指定缓存
- `GET /api/storage/cache/{cacheKey}/exists` - 检查文件是否已缓存

### 统计功能

- `GET /api/storage/stats/config/{configId}` - 获取配置统计信息
- `GET /api/storage/stats/type/{configId}` - 获取文件类型统计
- `GET /api/storage/stats/trend/{configId}` - 获取上传趋势统计

## 配置说明

### 存储配置

```yaml
storage:
  upload:
    max-file-size: 104857600  # 100MB
    max-file-count: 10
    temp-dir: /tmp/storage
    enable-multipart: true
    chunk-size: 5242880  # 5MB
    
  preview:
    enabled: true
    url-expiration: 3600  # 1小时
    max-preview-size: 52428800  # 50MB
    max-text-lines: 1000
    thumbnail-size: 200
    
  security:
    enable-file-type-check: true
    enable-file-size-limit: true
    enable-filename-sanitization: true
    enable-virus-scan: false
    
  cache:
    enabled: true
    expiration: 300  # 5分钟
    max-entries: 1000
    cache-dir: /tmp/storage-cache
```

## 使用示例

### 1. 配置MinIO存储

```json
{
  "name": "MinIO本地存储",
  "type": "MINIO",
  "endpoint": "http://localhost:9000",
  "accessKeyId": "minioadmin",
  "accessKeySecret": "minioadmin",
  "region": "us-east-1",
  "defaultBucket": "default",
  "enabled": true
}
```

### 2. 配置AWS S3

```json
{
  "name": "AWS S3存储",
  "type": "S3",
  "endpoint": "https://s3.amazonaws.com",
  "accessKeyId": "your-access-key-id",
  "accessKeySecret": "your-secret-access-key",
  "region": "us-east-1",
  "defaultBucket": "my-bucket",
  "enabled": true
}
```

## 前端功能

- 存储配置管理界面
- 文件上传（支持拖拽）
- 文件列表浏览
- 文件夹导航
- 文件下载
- 文件删除
- 文件夹创建
- **文件预览**
- **批量操作**
- **文件搜索**
- **缓存管理**

## 新增功能

### 1. 文件缓存管理
- 自动缓存常用文件
- 缓存统计信息
- 缓存清理功能
- 缓存命中率监控

### 2. 上传进度监控
- 实时上传进度显示
- 上传速度计算
- 预计剩余时间
- 上传状态跟踪

### 3. 文件类型检测
- 支持多种文件类型
- MIME类型自动识别
- 文件类型安全检查
- 预览类型判断

### 4. 全局异常处理
- 统一异常处理
- 详细错误信息
- 异常日志记录
- 用户友好提示

### 5. 存储统计功能
- 文件数量统计
- 存储空间统计
- 文件类型分布
- 上传趋势分析

## 部署说明

### 打包

```bash
mvn clean package
```

### 运行

```bash
java -jar target/one-agent-4j-storage-0.0.1-SNAPSHOT.jar
```

## 注意事项

1. 确保存储服务的访问密钥配置正确
2. 文件上传大小限制为100MB，可在配置文件中调整
3. 建议在生产环境中使用HTTPS
4. 定期备份数据库中的文件元数据
5. **缓存目录需要足够的磁盘空间**
6. **临时文件目录需要定期清理**

## 故障排除

### 常见问题

1. **连接失败**
   - 检查端点URL是否正确
   - 验证访问密钥是否有效
   - 确认网络连接正常

2. **上传失败**
   - 检查文件大小是否超限
   - 确认存储桶是否存在
   - 验证权限配置

3. **前端无法访问**
   - 确认后端服务已启动
   - 检查端口是否被占用
   - 验证CORS配置

4. **缓存问题**
   - 检查缓存目录权限
   - 确认磁盘空间充足
   - 验证缓存配置正确

## 开发说明

### 项目结构

```
src/main/java/com/all/in/one/agent/storage/
├── controller/          # 控制器
├── service/            # 服务层
├── mapper/             # 数据访问层
├── entity/             # 实体类
├── dto/                # 数据传输对象
├── util/               # 工具类
├── config/             # 配置类
├── exception/          # 异常处理
└── security/           # 安全相关
```

### 扩展存储类型

要添加新的存储类型，需要：

1. 在 `StorageConfig` 实体中添加新的类型
2. 在 `S3ClientUtil` 中添加相应的客户端创建逻辑
3. 在前端界面中添加新的选项

### 添加新的文件类型支持

1. 在 `FileTypeUtils` 中添加新的MIME类型映射
2. 在 `FilePreviewService` 中添加预览支持
3. 在配置文件中更新允许的文件类型列表

## 许可证

本项目采用 MIT 许可证。 