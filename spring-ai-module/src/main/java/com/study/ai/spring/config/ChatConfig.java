package com.study.ai.spring.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chat 相关配置
 * <p>
 * 提供 ChatMemory Bean，用于存储对话历史，支持多轮对话。
 * </p>
 */
@Configuration
public class ChatConfig {

    /**
     * 创建基于内存的 ChatMemory Bean
     * <p>
     * 生产环境可替换为持久化实现（如 Redis、数据库等）。
     * </p>
     */
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }
}
