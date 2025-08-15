package com.hinadt.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import com.hinadt.AusukaAiMod;
import net.minecraft.server.MinecraftServer;
import com.hinadt.persistence.MyBatisSupport;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI运行时系统 - 支持多种AI模型提供商
 * 支持DeepSeek、OpenAI、Claude等主流AI服务
 */
public final class AiRuntime {
    public static ChatClient AIClient;
    private static ConversationMemorySystem conversationMemory;
    private static ModAdminSystem modAdminSystem;
    // Dedicated pool for AI work to avoid ForkJoin common pool starvation
    public static ExecutorService AI_EXECUTOR;
    
    /**
     * 支持的AI提供商类型
     */
    public enum AiProvider {
        DEEPSEEK("deepseek", "DEEPSEEK_API_KEY"),
        OPENAI("openai", "OPENAI_API_KEY"),
        CLAUDE("claude", "CLAUDE_API_KEY"),
        CUSTOM("custom", "CUSTOM_API_KEY");
        
        private final String name;
        private final String envKeyName;
        
        AiProvider(String name, String envKeyName) {
            this.name = name;
            this.envKeyName = envKeyName;
        }
        
        public String getName() { return name; }
        public String getEnvKeyName() { return envKeyName; }
    }

    public static void init() {
        // 预加载配置
        AiConfig.load();

        // 自动检测可用的AI提供商
        AiProvider selectedProvider = detectAvailableProvider();
        ChatModel model = createChatModel(selectedProvider);

        if (model == null) {
            AusukaAiMod.LOGGER.warn("未能初始化AI模型，AI功能将被禁用。请在环境变量、JVM参数或配置文件中提供API密钥。");
        } else {
            AIClient = ChatClient.builder(model).build();
        }

        // Init a small, named thread pool for AI work
        if (AI_EXECUTOR == null || AI_EXECUTOR.isShutdown()) {
            AI_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r);
                t.setName("ausuka-ai-worker");
                t.setDaemon(true);
                return t;
            });
        }

        // 初始化持久层（建表等）并初始化对话记忆系统
        MyBatisSupport.init();
        conversationMemory = new ConversationMemorySystem();
        
        // ModAdminSystem 需要MinecraftServer，将在第一次访问时延迟初始化
        
        if (AIClient != null) {
            AusukaAiMod.LOGGER.info("AI运行时初始化完成，使用提供商: {}", selectedProvider.getName());
        } else {
            AusukaAiMod.LOGGER.info("AI运行时初始化完成（AI未启用）");
        }
    }
    
    /**
     * 自动检测可用的AI提供商
     * 按优先级顺序检查环境变量
     */
    private static AiProvider detectAvailableProvider() {
        // 首先尊重显式配置
        String preferred = AiConfig.getPreferredProvider();
        if (preferred != null) {
            for (AiProvider p : AiProvider.values()) {
                if (p.getName().equalsIgnoreCase(preferred)) {
                    AusukaAiMod.LOGGER.info("配置指定AI提供商: {}", p.getName());
                    return p;
                }
            }
            AusukaAiMod.LOGGER.warn("未知AI_PROVIDER='{}'，将自动检测", preferred);
        }

        // 按优先级检查
        for (AiProvider provider : AiProvider.values()) {
            String apiKey = AiConfig.get(provider.getEnvKeyName());
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                AusukaAiMod.LOGGER.info("检测到 {} API密钥，将使用此提供商", provider.getName());
                return provider;
            }
        }

        AusukaAiMod.LOGGER.warn("未检测到任何AI提供商的API密钥！");
        return AiProvider.DEEPSEEK; // 默认回退
    }
    
    /**
     * 根据提供商类型创建ChatModel
     */
    private static ChatModel createChatModel(AiProvider provider) {
        String apiKey = AiConfig.get(provider.getEnvKeyName());

        if (apiKey == null || apiKey.trim().isEmpty()) {
            AusukaAiMod.LOGGER.warn("提供商 {} 的API密钥未设置", provider.getName());
            return null;
        }
        
        try {
            switch (provider) {
                case DEEPSEEK:
                    return createDeepSeekModel(apiKey);
                    
                case OPENAI:
                    // TODO: 添加OpenAI支持
                    // return createOpenAiModel(apiKey);
                    AusukaAiMod.LOGGER.warn("OpenAI支持尚未实现，回退到DeepSeek");
                    return createDeepSeekModel(AiConfig.get("DEEPSEEK_API_KEY"));
                    
                case CLAUDE:
                    // TODO: 添加Claude支持
                    // return createClaudeModel(apiKey);
                    AusukaAiMod.LOGGER.warn("Claude支持尚未实现，回退到DeepSeek");
                    return createDeepSeekModel(AiConfig.get("DEEPSEEK_API_KEY"));
                    
                default:
                    AusukaAiMod.LOGGER.warn("未知的AI提供商: {}", provider.getName());
                    return null;
            }
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("创建 {} 模型失败", provider.getName(), e);
            return null;
        }
    }
    
    /**
     * 创建DeepSeek模型实例
     */
    private static ChatModel createDeepSeekModel(String apiKey) {
        var api = DeepSeekApi.builder().apiKey(apiKey).build();

        var options = DeepSeekChatOptions.builder()
                .model("deepseek-chat")
                .temperature(0.7)  // 添加一些创造性
                .maxTokens(4000)   // 控制输出长度
                .build();

        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(options)
                .build();
    }
    
    // TODO: 实现其他AI提供商
    /*
    private static ChatModel createOpenAiModel(String apiKey) {
        // OpenAI implementation when spring-ai-openai is available
        throw new UnsupportedOperationException("OpenAI支持开发中");
    }
    
    private static ChatModel createClaudeModel(String apiKey) {
        // Claude implementation when spring-ai-claude is available  
        throw new UnsupportedOperationException("Claude支持开发中");
    }
    */
    
    /**
     * 获取对话记忆系统
     */
    public static ConversationMemorySystem getConversationMemory() {
        return conversationMemory;
    }
    
    /**
     * 获取MOD管理员系统 - 延迟初始化
     */
    public static ModAdminSystem getModAdminSystem() {
        return modAdminSystem;
    }
    
    /**
     * 初始化MOD管理员系统（需要服务器实例）
     */
    public static void initModAdminSystem(MinecraftServer server) {
        if (modAdminSystem == null) {
            try {
                modAdminSystem = new ModAdminSystem(server);
                AusukaAiMod.LOGGER.info("MOD管理员系统初始化完成");
            } catch (Exception e) {
                AusukaAiMod.LOGGER.error("MOD管理员系统初始化失败", e);
            }
        }
    }
    
    /**
     * 关闭AI运行时
     */
    public static void shutdown() {
        if (AI_EXECUTOR != null) {
            AI_EXECUTOR.shutdownNow();
            AI_EXECUTOR = null;
        }
        if (conversationMemory != null) {
            conversationMemory.shutdown();
        }
        if (modAdminSystem != null) {
            // ModAdminSystem doesn't need shutdown, it uses shared connection
            modAdminSystem = null;
        }
    }

    /**
     * 是否可用（是否已初始化AI Client）
     */
    public static boolean isReady() {
        return AIClient != null;
    }
}
