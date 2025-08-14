package com.hinadt.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

public final class AiRuntime {
    public static ChatClient AIClient;
    private static ConversationMemorySystem conversationMemory;

    public static void init() {
        var apiKey = System.getenv("DEEPSEEK_API_KEY");
        var api = DeepSeekApi.builder().apiKey(apiKey).build();

        var options = DeepSeekChatOptions.builder()
                .model("deepseek-chat")
                .build();

        var model = DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(options)
                .build();
                
        AIClient = ChatClient.builder(model).build();
        
        // 初始化对话记忆系统
        conversationMemory = new ConversationMemorySystem();
    }
    
    /**
     * 获取对话记忆系统
     */
    public static ConversationMemorySystem getConversationMemory() {
        return conversationMemory;
    }
    
    /**
     * 关闭AI运行时
     */
    public static void shutdown() {
        if (conversationMemory != null) {
            conversationMemory.shutdown();
        }
    }
}
