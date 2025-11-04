package com.all.in.one.agent.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * AI 去噪服务接口
 * <p>
 * 使用 LangChain4J AiServices 定义
 * </p>
 *
 * @author One Agent 4J
 */
public interface DenoiseAiService {

    /**
     * 判断异常是否需要报警
     *
     * @param prompt 包含新异常和历史异常的完整提示词（从资源文件加载）
     * @return AI 的 JSON 响应
     */
    @SystemMessage("""
            你是一个异常监控系统的智能去噪助手。
            你的任务是判断新发生的异常是否需要报警。

            请分析异常信息，并以 JSON 格式返回判断结果。
            只返回 JSON，不要包含其他内容。
            """)
    String analyzeException(@UserMessage String prompt);
}
