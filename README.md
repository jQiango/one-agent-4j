# One Agent 4J

一个基于 Spring Boot + LangChain4J 的 AI Agent 应用，支持与大语言模型交互。

## 🏗️ 项目结构

```
one-agent-4j/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/all/in/one/agent/
│   │   │       ├── Application.java                    # 启动类
│   │   │       ├── model/
│   │   │       │   └── ChatModelService.java          # AI 聊天模型服务
│   │   │       └── test/
│   │   │           ├── controller/
│   │   │           │   └── DemoController.java        # 演示控制器
│   │   │           └── memory/
│   │   │               └── PersistentChatMemoryStore.java  # 持久化聊天记忆
│   │   └── resources/
│   │       ├── application.properties                  # 应用配置
│   │       └── init/
│   │           └── init.sql                           # 数据库初始化脚本
│   └── test/                                          # 测试代码
├── pom.xml                                            # Maven 配置
└── README.md                                          # 项目说明
```

## 🚀 快速开始

### 1. 环境要求
- JDK 17+
- Maven 3.6+
- MySQL 8.0+

### 2. 克隆项目
```bash
git clone <repository-url>
cd one-agent-4j
```

### 3. 数据库配置
1. 创建 MySQL 数据库
2. 执行 `src/main/resources/init/init.sql` 脚本
3. 修改 `src/main/resources/application.properties` 中的数据库连接信息

### 4. 配置 AI 模型
在 `application.properties` 中配置 LangChain4J 相关参数：
```properties
# OpenAI API 配置（或其他兼容的 API）
langchain4j.open-ai.api-key=your-api-key
langchain4j.open-ai.base-url=https://api.openai.com/v1
langchain4j.open-ai.model-name=gpt-3.5-turbo
```

### 5. 编译和运行
```bash
# 编译项目
mvn clean compile

# 打包
mvn clean package

# 运行应用
mvn spring-boot:run
```

或者使用 Maven Wrapper：
```bash
# Windows
mvnw.cmd spring-boot:run

# Linux/Mac
./mvnw spring-boot:run
```

### 6. 访问应用
- 应用地址: http://localhost:8080

## 🔧 技术栈

- **框架**: Spring Boot 3.4.8
- **AI 集成**: LangChain4J 1.1.0
  - langchain4j-core
  - langchain4j-open-ai
- **响应式**: Spring WebFlux
- **数据库**: MySQL 9.3.0
- **ORM**: MyBatis-Plus 3.5.12
- **构建工具**: Maven
- **其他**:
  - Lombok
  - FastJson2
  - Apache Commons Collections4

## 💡 核心功能

### AI 聊天服务
- 集成 LangChain4J，支持与大语言模型交互
- 支持多种 AI 模型提供商（OpenAI 兼容接口）
- 提供聊天记忆存储，支持上下文对话

### 数据持久化
- 使用 MyBatis-Plus 进行数据访问
- 支持聊天记录持久化存储
- 灵活的数据库操作

## 🏃‍♂️ 开发指南

### 项目架构
本项目采用单体架构，代码组织清晰：
- `Application.java` - Spring Boot 启动入口
- `model/` - AI 模型相关服务
- `controller/` - REST API 控制器
- `memory/` - 聊天记忆存储实现

### 构建命令
```bash
# 编译
mvn clean compile

# 打包
mvn clean package

# 跳过测试打包
mvn clean package -DskipTests

# 安装到本地仓库
mvn clean install
```

### 添加新功能
1. 在相应包下添加代码
2. 遵循 Spring Boot 最佳实践
3. 使用依赖注入管理组件

## 📦 相关项目

- [one-agent-4j-storage](../one-agent-4j-storage) - 对象存储模块（独立项目）
  - 支持 S3/MinIO 等对象存储服务
  - 文件上传下载管理
  - 存储统计功能

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交代码 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 发起 Pull Request

## 📄 许可证

[MIT License](LICENSE)

## 📞 联系方式

如有问题，请提交 Issue 或联系项目维护者。
