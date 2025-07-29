# One Agent 4J - Maven多模块项目

一个基于Spring Boot + MyBatis-Plus + LangChain4J的多模块Java项目。

## 🏗️ 项目结构

```
one-agent-4j-parent/
├── one-agent-4j-common/     # 公共模块（工具类、常量）
├── one-agent-4j-entity/     # 实体模块（数据库实体）
├── one-agent-4j-dao/        # 数据访问层（Mapper）
├── one-agent-4j-service/    # 服务层（业务逻辑）
└── one-agent-4j-web/        # Web层（控制器、启动类）
```

## 🚀 快速开始

### 1. 环境要求
- JDK 17+
- Maven 3.6+
- MySQL 8.0+

### 2. 克隆项目
```bash
git clone <repository-url>
cd one-agent-4j-parent
```

### 3. 数据库配置
1. 创建MySQL数据库
2. 执行 `one-agent-4j-web/src/main/resources/sql/init.sql` 脚本
3. 修改 `one-agent-4j-web/src/main/resources/application.properties` 中的数据库连接信息

### 4. 编译和运行
```bash
# 编译所有模块
mvn clean install

# 运行应用
cd one-agent-4j-web
mvn spring-boot:run
```

### 5. 访问应用
- 应用地址: http://localhost:8080
- API文档: 查看下方API接口说明

## 📋 API接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/users` | 获取所有用户 |
| GET | `/api/users/{id}` | 根据ID获取用户 |
| GET | `/api/users/username/{username}` | 根据用户名获取用户 |
| GET | `/api/users/status/{status}` | 根据状态获取用户列表 |
| GET | `/api/users/page` | 分页查询用户 |
| POST | `/api/users` | 创建用户 |
| PUT | `/api/users/{id}` | 更新用户 |
| DELETE | `/api/users/{id}` | 删除用户 |

## 🔧 技术栈

- **框架**: Spring Boot 3.4.8
- **数据库**: MySQL 9.3.0
- **ORM**: MyBatis-Plus 3.5.12
- **AI**: LangChain4J 1.1.0
- **构建工具**: Maven
- **其他**: Lombok, FastJson2

## 📚 详细文档

- [Maven多模块项目说明](Maven多模块项目说明.md)
- [MyBatis-Plus配置说明](MyBatis-Plus配置说明.md)

## 🏃‍♂️ 开发指南

### 添加新功能
1. 在相应模块中添加代码
2. 遵循分层架构原则
3. 确保依赖关系正确

### 模块依赖顺序
```
web → service → dao → entity → common
```

### 构建命令
```bash
# 编译
mvn clean compile

# 打包
mvn clean package

# 安装到本地仓库
mvn clean install

# 只构建特定模块
mvn clean package -pl one-agent-4j-web -am
```

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支
3. 提交代码
4. 发起 Pull Request

## 📄 许可证

[MIT License](LICENSE)

## 📞 联系方式

如有问题，请提交 Issue 或联系项目维护者。 