package com.all.in.one.agent.config;

import com.all.in.one.agent.ai.service.DenoiseAiService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4J 配置
 *
 * @author One Agent 4J
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "one-agent.ai-denoise", name = "enabled", havingValue = "true")
public class LangChain4JConfig {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;

    @Value("${langchain4j.open-ai.chat-model.temperature:0.7}")
    private Double temperature;

    @Value("${langchain4j.open-ai.chat-model.max-tokens:2000}")
    private Integer maxTokens;

    @Value("${langchain4j.open-ai.chat-model.timeout:60}")
    private Long timeoutSeconds;

    @Bean
    public OpenAiChatModel openAiChatModel() {
        log.info("初始化 OpenAiChatModel - baseUrl={}, modelName={}", baseUrl, modelName);

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public DenoiseAiService denoiseAiService(OpenAiChatModel openAiChatModel) {
        log.info("初始化 DenoiseAiService");

        return AiServices.create(DenoiseAiService.class, openAiChatModel);
    }
}
