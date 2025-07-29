package com.all.in.one.agent.controller;

import com.all.in.one.agent.common.result.Result;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI聊天演示控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
public class Demo01Controller {

    /**
     * 简单的AI聊天接口
     */
    @GetMapping("/hello")
    public Result<Map<String, Object>> hello() {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://api.siliconflow.cn")
                .apiKey("sk-nfcjvrdsczvnkplcdcdprvhpzclbahcxgjjkhafawmzzftiq")
                .modelName("deepseek-ai/DeepSeek-V3")
                .build();

        SystemMessage systemMessage = SystemMessage.from("你是一个天气预报助手，回答用户关于天气的问题。");
        UserMessage userMessage = UserMessage.from("你能告诉我今天杭州的天气吗");

        ChatResponse chat = model.chat(List.of(systemMessage, userMessage));
        
        // 提取ChatResponse中的相关数据并返回可序列化的Map
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("content", chat.aiMessage().text());
        responseData.put("tokenUsage", Map.of(
            "inputTokens", chat.tokenUsage() != null ? chat.tokenUsage().inputTokenCount() : 0,
            "outputTokens", chat.tokenUsage() != null ? chat.tokenUsage().outputTokenCount() : 0,
            "totalTokens", chat.tokenUsage() != null ? chat.tokenUsage().totalTokenCount() : 0
        ));
        responseData.put("finishReason", chat.finishReason() != null ? chat.finishReason().toString() : null);
        
        return Result.success("AI聊天成功", responseData);
    }
}