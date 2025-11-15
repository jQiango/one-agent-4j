# 配置文件合并说明

## ✅ 已完成的操作

### 1. 优化了 `application.properties`

**改进点**:
- ✅ 清晰的结构划分（必填配置区 + 可选配置区）
- ✅ 添加图标标识（📌 ✅ ⚙️ 🔻 📝 💾 📋 🎯）提高可读性
- ✅ 按降噪层级组织（Layer 0/1/1.5/2）
- ✅ 详细的注释说明每个配置项的作用
- ✅ 底部添加快速开始指南

**新的配置文件特点**:
```properties
# ========================================
# ✅ 必填配置区（只需配置2项）
# ========================================
1. spring.datasource.*        # 数据库连接
2. langchain4j.open-ai.*      # AI API Key

# ========================================
# ⚙️ 可选配置区（所有功能默认启用）
# ========================================
3. 各层降噪策略              # 按需调整
4. HTTP 日志配置            # 按需调整
```

---

## ⚠️ 需要手动操作

### 删除 `application-minimal.properties` 文件

**文件位置**:
```
src/main/resources/application-minimal.properties
```

**删除原因**:
1. 配置已合并到 `application.properties`
2. 避免配置文件冗余
3. 统一使用一个配置文件，降低维护成本

**删除方法**:

#### 方法1: 使用 Windows 资源管理器
1. 打开文件夹: `F:\work\ai\one-agent-4j\src\main\resources\`
2. 找到 `application-minimal.properties` 文件
3. 右键 → 删除

#### 方法2: 使用 IDEA
1. 在 Project 窗口中展开 `src/main/resources`
2. 右键点击 `application-minimal.properties`
3. 选择 Delete → 确认

#### 方法3: 使用 Git
```bash
git rm src/main/resources/application-minimal.properties
git commit -m "合并配置文件，删除 application-minimal.properties"
```

#### 方法4: 使用命令行
```bash
# Windows CMD
del src\main\resources\application-minimal.properties

# Windows PowerShell
Remove-Item src\main\resources\application-minimal.properties

# Git Bash
rm src/main/resources/application-minimal.properties
```

---

## 📊 配置文件对比

### 之前：2个配置文件

| 文件名 | 行数 | 用途 | 问题 |
|--------|------|------|------|
| `application.properties` | 96行 | 完整配置示例 | 引用了 minimal 文件 |
| `application-minimal.properties` | 38行 | 最小化配置 | 冗余 |

### 现在：1个配置文件

| 文件名 | 行数 | 用途 | 优点 |
|--------|------|------|------|
| `application.properties` | 145行 | 统一配置文件 | ✅ 清晰的必填/可选区分<br>✅ 详细的注释说明<br>✅ 快速开始指南<br>✅ 按层级组织 |

---

## 🎯 新配置文件的使用

### 快速开始（3步）

```properties
# 1️⃣ 修改数据库连接
spring.datasource.url=jdbc:mysql://localhost:3306/one_agent...
spring.datasource.username=root
spring.datasource.password=your_password

# 2️⃣ 配置 AI API Key
langchain4j.open-ai.chat-model.api-key=your-api-key

# 3️⃣ 启动应用
```

**就这么简单！** 所有功能自动启用：
- ✅ 异常自动捕获
- ✅ HTTP 请求日志
- ✅ 多层降噪（Layer 0/1/1.5/2）
- ✅ 异常持久化
- ✅ 工单生成

### 高级配置（按需）

如果需要调整某些功能，取消注释对应配置项：

```properties
# 关闭 AI 去噪
one-agent.ai-denoise.enabled=false

# 调整指纹去重时间窗口
one-agent.dedup.time-window-minutes=5

# 启用规则引擎频率限制
one-agent.rule-engine.frequency-limit.enabled=true
one-agent.rule-engine.frequency-limit.max-count=10
```

---

## 📚 相关文档

删除 `application-minimal.properties` 后，相关文档中的引用已不存在，无需更新其他文件。

所有配置说明都在 `application.properties` 文件顶部的注释中。

---

## ✅ 检查清单

完成以下步骤后，配置合并即完成：

- [ ] 删除 `src/main/resources/application-minimal.properties` 文件
- [ ] 确认 `application.properties` 文件存在且格式正确
- [ ] 重新启动应用，验证配置生效

---

**配置合并完成！** 🎉

现在项目只使用一个配置文件，更加清晰简洁。
