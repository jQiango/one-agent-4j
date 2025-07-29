# MyBatis-Plus 配置说明

## 项目结构

```
src/main/java/com/all/in/one/agent/
├── entity/           # 实体类
│   └── User.java
├── mapper/           # Mapper接口
│   └── UserMapper.java
├── service/          # 服务层
│   ├── IUserService.java
│   └── impl/
│       └── UserServiceImpl.java
├── controller/       # 控制器层
│   └── UserController.java
└── Application.java  # 启动类
```

## 1. 依赖配置

已在 `pom.xml` 中添加以下依赖：

```xml
<!-- MyBatis-Plus -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.12</version>
</dependency>

<!-- MySQL驱动 -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>9.3.0</version>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.28</version>
</dependency>
```

## 2. 数据库配置

在 `application.properties` 中配置：

```properties
# 数据库配置 - 请根据实际情况修改
spring.datasource.url=jdbc:mysql://localhost:3306/one_agent_4j?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# MyBatis-Plus配置
mybatis-plus.configuration.map-underscore-to-camel-case=true
mybatis-plus.global-config.db-config.id-type=ASSIGN_ID
mybatis-plus.global-config.db-config.logic-delete-field=deleted
mybatis-plus.global-config.db-config.logic-delete-value=1
mybatis-plus.global-config.db-config.logic-not-delete-value=0
```

## 3. 启动类配置

在 `Application.java` 中添加 `@MapperScan` 注解：

```java
@SpringBootApplication
@MapperScan("com.all.in.one.agent.mapper")
public class Application {
    // ...
}
```

## 4. 数据库初始化

执行 `src/main/resources/sql/init.sql` 中的SQL脚本来创建数据库表和示例数据。

## 5. 核心功能展示

### 实体类 (User.java)
- 使用 `@TableName` 指定表名
- 使用 `@TableId` 配置主键生成策略
- 使用 `@TableField` 配置字段映射
- 使用 `@TableLogic` 配置逻辑删除
- 使用 `@Version` 配置乐观锁

### Mapper接口 (UserMapper.java)
- 继承 `BaseMapper<User>` 获得基础CRUD方法
- 自定义查询方法使用 `@Select` 注解
- 支持动态SQL查询

### Service层
- `IUserService` 继承 `IService<User>`
- `UserServiceImpl` 继承 `ServiceImpl<UserMapper, User>`
- 提供业务逻辑方法

### Controller层
- 提供完整的REST API接口
- 支持CRUD操作和分页查询

## 6. API 接口说明

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
| DELETE | `/api/users/batch` | 批量删除用户 |

## 7. 使用示例

### 分页查询
```
GET /api/users/page?current=1&size=10&username=admin&status=1
```

### 创建用户
```json
POST /api/users
{
    "username": "newuser",
    "password": "password123",
    "email": "newuser@example.com",
    "phone": "13800138888"
}
```

### 更新用户
```json
PUT /api/users/1
{
    "email": "updated@example.com",
    "phone": "13900139999"
}
```

## 8. MyBatis-Plus 特性

1. **无侵入**: 只做增强不做改变，不会对现有工程产生影响
2. **损耗小**: 启动即会自动注入基本CURD，性能基本无损耗
3. **强大的CRUD操作**: 内置通用Mapper，少量配置即可实现单表大部分CRUD操作
4. **支持Lambda形式调用**: 通过Lambda表达式，方便的编写各类查询条件
5. **支持主键自动生成**: 支持多种主键策略
6. **支持ActiveRecord模式**: 实体类只需继承Model类即可进行强大的CRUD操作
7. **支持自定义全局通用操作**: 支持全局通用方法注入
8. **内置代码生成器**: 采用代码或者Maven插件可快速生成Mapper、Model、Service、Controller层代码
9. **内置分页插件**: 基于MyBatis物理分页，开发者无需关心具体操作
10. **分页插件支持多种数据库**: 支持MySQL、MariaDB、Oracle、DB2、H2、HSQL、SQLite、PostgreSQL、SQLServer等多种数据库
11. **内置性能分析插件**: 可输出SQL语句以及其执行时间
12. **内置全局拦截插件**: 提供全表delete、update操作智能分析阻断

## 9. 注意事项

1. 请根据实际情况修改数据库连接信息
2. 密码字段使用了BCrypt加密，示例密码为 "123456"
3. 逻辑删除功能已配置，删除操作会自动设置deleted字段
4. 版本号字段用于乐观锁控制并发更新
5. 开发环境已开启SQL日志打印，生产环境建议关闭

## 10. 下一步扩展

1. 添加更多业务实体类
2. 配置数据源连接池（如HikariCP）
3. 添加Redis缓存支持
4. 集成Spring Security进行权限控制
5. 添加数据校验和异常处理
6. 集成Swagger生成API文档 