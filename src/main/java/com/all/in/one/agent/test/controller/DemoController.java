package com.all.in.one.agent.test.controller;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
public class DemoController {
    public static final OpenAiChatModel model = OpenAiChatModel.builder()
            .baseUrl("https://api.siliconflow.cn")
            .apiKey("sk-nfcjvrdsczvnkplcdcdprvhpzclbahcxgjjkhafawmzzftiq")
            .modelName("Qwen/Qwen2.5-VL-72B-Instruct")
            .build();
    private final SystemMessage systemMessage = SystemMessage.from("你是一个天气预报助手，回答用户关于天气的问题。");

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

}
