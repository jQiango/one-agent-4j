KE# 配置说明

## API Key 配置

本项目使用 LangChain4J 集成大语言模型，需要配置 API Key 才能正常工作。

### 方式一：使用环境变量（推荐）

**优点**：API Key 不会被提交到代码仓库，更安全

#### Windows
```cmd
# 临时设置（仅当前会话有效）
set OPENAI_API_KEY=your-actual-api-key-here

# 永久设置
setx OPENAI_API_KEY "your-actual-api-key-here"
```

#### Linux/Mac
```bash
# 临时设置（仅当前会话有效）
export OPENAI_API_KEY=your-actual-api-key-here

# 永久设置（添加到 ~/.bashrc 或 ~/.zshrc）
echo 'export OPENAI_API_KEY=your-actual-api-key-here' >> ~/.bashrc
source ~/.bashrc
```

### 方式二：直接在配置文件中设置

编辑 `src/main/resources/application.properties`：

```properties
langchain4j.open-ai.chat-model.api-key=your-actual-api-key-here
```

⚠️ **注意**：如果使用此方式，请确保不要将包含真实 API Key 的配置文件提交到 Git 仓库！

## 支持的 AI 服务提供商

### 1. SiliconFlow（默认）

```properties
langchain4j.open-ai.chat-model.base-url=https://api.siliconflow.cn
langchain4j.open-ai.chat-model.model-name=Qwen/Qwen2.5-VL-72B-Instruct
```

**获取 API Key**：访问 [SiliconFlow](https://siliconflow.cn/) 注册并获取

**常用模型**：
- `Qwen/Qwen2.5-VL-72B-Instruct` - 支持视觉的大模型
- `Qwen/QwQ-32B` - 推理能力强的模型
- `deepseek-ai/DeepSeek-V3` - DeepSeek V3 模型

### 2. OpenAI

```properties
langchain4j.open-ai.chat-model.base-url=https://api.openai.com/v1
langchain4j.open-ai.chat-model.model-name=gpt-3.5-turbo
```

**获取 API Key**：访问 [OpenAI](https://platform.openai.com/) 注册并获取

**常用模型**：
- `gpt-3.5-turbo` - 性价比高
- `gpt-4` - 能力更强
- `gpt-4-turbo` - 速度和能力的平衡

### 3. DeepSeek

```properties
langchain4j.open-ai.chat-model.base-url=https://api.deepseek.com
langchain4j.open-ai.chat-model.model-name=deepseek-chat
```

**获取 API Key**：访问 [DeepSeek](https://www.deepseek.com/) 注册并获取

### 4. 其他兼容 OpenAI API 的服务

只要是兼容 OpenAI API 格式的服务，都可以通过修改 `base-url` 和 `model-name` 来使用。

## 配置参数说明

| 参数 | 说明 | 默认值 | 必填 |
|------|------|--------|------|
| `api-key` | API 密钥 | - | 是 |
| `base-url` | API 基础地址 | https://api.siliconflow.cn | 是 |
| `model-name` | 模型名称 | Qwen/Qwen2.5-VL-72B-Instruct | 是 |
| `temperature` | 温度参数 (0.0-2.0) | 0.7 | 否 |
| `max-tokens` | 最大生成 tokens | 2000 | 否 |
| `timeout` | 请求超时时间 | 60s | 否 |

## 常见问题

### 1. "Api key is invalid" 错误

**原因**：
- API Key 未配置或配置错误
- API Key 已过期或被撤销
- 使用了错误的 API 服务地址

**解决方案**：
1. 检查 API Key 是否正确配置
2. 验证 API Key 在对应平台上是否有效
3. 确认 `base-url` 与 API Key 的来源匹配

### 2. 如何更换 AI 模型？

修改 `application.properties` 中的配置：

```properties
langchain4j.open-ai.chat-model.model-name=你想要的模型名称
```

### 3. 如何调整输出的随机性？

修改 `temperature` 参数：
- 接近 0：输出更确定、一致
- 接近 2：输出更随机、创造性

```properties
langchain4j.open-ai.chat-model.temperature=0.5
```

## 测试 API 配置

启动应用后，访问：

```bash
# 测试文本聊天
curl http://localhost:8080/hello

# 测试多模态（图片+文字）
curl http://localhost:8080/image-text
```

如果配置正确，应该能收到 AI 的回复。
