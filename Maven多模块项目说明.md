# Maven多模块项目重构说明

## 项目结构

```
one-agent-4j-parent/
├── pom.xml                          # 父模块POM，管理依赖版本
├── one-agent-4j-common/             # 公共模块
│   ├── pom.xml
│   └── src/main/java/com/all/in/one/agent/
│       └── common/
│           ├── constant/            # 常量类
│           └── result/              # 响应结果类
├── one-agent-4j-entity/             # 实体模块
│   ├── pom.xml
│   └── src/main/java/com/all/in/one/agent/
│       └── entity/                  # 数据库实体类
├── one-agent-4j-dao/                # 数据访问层
│   ├── pom.xml
│   └── src/main/java/com/all/in/one/agent/
│       └── mapper/                  # MyBatis Mapper接口
├── one-agent-4j-service/            # 服务层
│   ├── pom.xml
│   └── src/main/java/com/all/in/one/agent/
│       └── service/                 # 业务逻辑接口和实现
│           └── impl/
└── one-agent-4j-web/                # Web层
    ├── pom.xml
    └── src/main/
        ├── java/com/all/in/one/agent/
        │   ├── Application.java     # 启动类
        │   └── controller/          # 控制器
        └── resources/
            ├── application.properties
            └── sql/init.sql
```

## 模块依赖关系

```
one-agent-4j-web
    ↓ 依赖
one-agent-4j-service
    ↓ 依赖  
one-agent-4j-dao
    ↓ 依赖
one-agent-4j-entity
    ↓ 依赖
one-agent-4j-common
```

## 各模块说明

### 1. one-agent-4j-parent (父模块)
- **作用**: 统一管理所有子模块的依赖版本
- **关键配置**:
  - `<packaging>pom</packaging>`: 表示这是一个父模块
  - `<modules>`: 定义所有子模块
  - `<dependencyManagement>`: 统一管理依赖版本

### 2. one-agent-4j-common (公共模块)
- **作用**: 存放通用工具类、常量、配置等
- **包含内容**:
  - `CommonConstants`: 公共常量类
  - `Result<T>`: 统一响应结果类
  - 工具类、枚举等
- **依赖**: 仅依赖基础框架，如Spring Boot、Lombok等

### 3. one-agent-4j-entity (实体模块)
- **作用**: 存放数据库实体类
- **包含内容**:
  - `User`: 用户实体类
  - 其他数据库实体类
- **依赖**: 
  - `one-agent-4j-common`
  - MyBatis-Plus注解
  - Lombok

### 4. one-agent-4j-dao (数据访问层)
- **作用**: 数据访问接口，与数据库交互
- **包含内容**:
  - `UserMapper`: 用户数据访问接口
  - 其他Mapper接口
- **依赖**:
  - `one-agent-4j-entity`
  - MyBatis-Plus
  - 数据库驱动

### 5. one-agent-4j-service (服务层)
- **作用**: 业务逻辑处理
- **包含内容**:
  - `IUserService`: 用户服务接口
  - `UserServiceImpl`: 用户服务实现类
- **依赖**:
  - `one-agent-4j-dao`
  - Spring事务管理

### 6. one-agent-4j-web (Web层)
- **作用**: 对外提供API接口，是应用的入口
- **包含内容**:
  - `Application`: Spring Boot启动类
  - `UserController`: 用户控制器
  - 配置文件
- **依赖**:
  - `one-agent-4j-service`
  - Spring Boot Web
  - LangChain4J

## 构建和运行

### 1. 编译整个项目
```bash
# 在父模块目录执行
mvn clean compile
```

### 2. 打包项目
```bash
# 打包所有模块
mvn clean package

# 只打包特定模块
mvn clean package -pl one-agent-4j-web
```

### 3. 安装到本地仓库
```bash
# 安装所有模块到本地Maven仓库
mvn clean install
```

### 4. 运行应用
```bash
# 方式1: 直接运行
cd one-agent-4j-web
mvn spring-boot:run

# 方式2: 运行打包后的jar
java -jar one-agent-4j-web/target/one-agent-4j-web-0.0.1-SNAPSHOT.jar
```

## 多模块项目的优势

### 1. 职责分离
- 每个模块有明确的职责边界
- 便于团队协作开发
- 代码结构更清晰

### 2. 依赖管理
- 强制依赖方向，避免循环依赖
- 统一版本管理，避免版本冲突
- 模块化部署

### 3. 可维护性
- 模块独立测试
- 部分模块升级不影响其他模块
- 便于代码重用

### 4. 扩展性
- 新增功能可以独立模块开发
- 便于微服务拆分
- 支持差异化部署

## 开发规范

### 1. 依赖规则
- 只能依赖下层模块，不能反向依赖
- 同层模块之间不能相互依赖
- 尽量减少跨层依赖

### 2. 包命名规范
- 所有模块统一使用 `com.all.in.one.agent` 作为根包
- 各模块按功能划分子包

### 3. 版本管理
- 所有模块使用相同版本号
- 在父模块中统一管理第三方依赖版本
- 使用 `${project.version}` 引用内部模块

## 常见问题

### 1. 模块间找不到类
- 确保依赖关系正确配置
- 执行 `mvn clean install` 安装依赖模块

### 2. 循环依赖
- 检查模块依赖关系
- 将共同依赖提取到下层模块

### 3. 版本冲突
- 在父模块的 `dependencyManagement` 中统一管理版本
- 子模块中不要指定版本号

## 扩展建议

### 1. 添加更多模块
- `one-agent-4j-config`: 配置模块
- `one-agent-4j-security`: 安全模块
- `one-agent-4j-admin`: 管理后台模块

### 2. 集成其他功能
- 添加Redis缓存支持
- 集成消息队列
- 添加定时任务模块

### 3. 微服务拆分
- 每个模块可以独立拆分为微服务
- 使用Spring Cloud进行服务注册发现
- API网关统一入口

这样的多模块结构为项目的长期发展和维护提供了良好的基础。 