package com.all.in.one.agent.test.controller;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
public class DemoController {

    private final OpenAiChatModel model;
    private final SystemMessage systemMessage = SystemMessage.from("你是一个天气预报助手，回答用户关于天气的问题。");

    public DemoController(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName) {
        this.model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
        log.info("OpenAI Chat Model initialized with base URL: {}, model: {}", baseUrl, modelName);
    }

    @GetMapping("/hello")
    public String hello() {
        UserMessage userMessage = UserMessage.from("你能告诉我今天杭州的天气吗");
        ChatResponse chat = model.chat(List.of(systemMessage, userMessage));
        log.info("Chat response: {}", chat.metadata());
        return chat.aiMessage().text();
    }

    //多模态测试，图片和文字
    @GetMapping("/image-text")
    public String imageText() {
        TextContent textContent = TextContent.from("你能告诉我图片的内容吗？");
        ImageContent imageContent = ImageContent.from("https://file.ljcdn.com/zqt-data-daily//data/pic_facade/generateTableWithQRCode/20250729192516299b956c-3680-4143-85c1-1f635f91550c.jpg");
        UserMessage userMessage = UserMessage.from(textContent,imageContent);
        ChatResponse chat = model.chat(userMessage);
        return chat.aiMessage().text();
    }

    // ========== 异常测试接口 ==========

    /**
     * 测试 NullPointerException
     */
    @GetMapping("/test/null-pointer")
    public String testNullPointer() {
        log.info("测试 NullPointerException");
        String str = null;
        return str.length() + ""; // 触发 NullPointerException
    }

    /**
     * 测试 ArrayIndexOutOfBoundsException
     */
    @GetMapping("/test/array-index")
    public String testArrayIndex() {
        log.info("测试 ArrayIndexOutOfBoundsException");
        int[] arr = {1, 2, 3};
        return arr[10] + ""; // 触发 ArrayIndexOutOfBoundsException
    }

    /**
     * 测试 ArithmeticException
     */
    @GetMapping("/test/arithmetic")
    public String testArithmetic() {
        log.info("测试 ArithmeticException");
        int result = 10 / 0; // 触发 ArithmeticException
        return result + "";
    }

    /**
     * 测试 RuntimeException
     */
    @GetMapping("/test/runtime")
    public String testRuntime() {
        log.info("测试 RuntimeException");
        throw new RuntimeException("这是一个运行时异常");
    }

}
