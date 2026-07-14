package com.study.ai.spring.controller;

import com.study.ai.spring.tools.StockTools;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/chat")
@RestController
public class ChatAgentController {

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ToolCallingManager toolCallingManager;

    @GetMapping("/chat0")
    public String chat0(String conversationId) {
        // 定义 ChatOptions
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                // 指定工具
                .toolCallbacks(ToolCallbacks.from(new StockTools()))
                .model("deepseek-v3")
                .build();

        // 定义提示词，要求按照 React 架构运行
        Prompt prompt = new Prompt(
                List.of(new SystemMessage("你是一个智能助手，你擅长使用工具帮我解决问题。"
                                + "约束：时间通过工具获取，不要捏造"),
                        new UserMessage("帮我分析最近三个月特斯拉（TSLA）的股价走势，"
                                + "并结合新闻事件解释可能的影响因素。")),
                chatOptions);

        // 添加提示词到记忆
        chatMemory.add(conversationId, prompt.getInstructions());

        Prompt promptWithMemory = new Prompt(chatMemory.get(conversationId, 100), chatOptions);

        // 调用模型
        ChatResponse chatResponse = chatModel.call(promptWithMemory);

        return chatResponse.getResult().getOutput().getText();
    }

}
