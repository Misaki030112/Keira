package com.hinadt.ai;

import com.hinadt.AusukaAiMod;
import com.hinadt.observability.LoggingContextPropagation;
import com.hinadt.persistence.MyBatisSupport;
import net.minecraft.server.MinecraftServer;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * AI runtime system - supports multiple AI model providers
 * Supports mainstream AI services such as DeepSeek, OpenAI, Claude, etc.
 */
public final class AiRuntime {
    public static ChatClient AIClient;
    private static ConversationMemorySystem conversationMemory;
    private static ModAdminSystem modAdminSystem;
    // Dedicated pool for AI work to avoid ForkJoin common pool starvation
    public static ExecutorService AI_EXECUTOR;
    // Lightweight scheduler for timeouts and delayed operations
    private static ScheduledExecutorService AI_SCHEDULER;
    
    /**
     * Supported AI provider types
     */
    public enum AiProvider {
        DEEPSEEK("deepseek", new String[]{"DEEPSEEK_API_KEY"}),
        OPENAI("openai", new String[]{"OPENAI_API_KEY"}),
        // Claude is provided by Anthropic; support both common env names
        CLAUDE("claude", new String[]{"CLAUDE_API_KEY", "ANTHROPIC_API_KEY"})

        ;

        private final String name;
        private final String[] envKeyNames;

        AiProvider(String name, String[] envKeyNames) {
            this.name = name;
            this.envKeyNames = envKeyNames;
        }

        public String getName() { return name; }
        public String[] getEnvKeyNames() { return envKeyNames; }
        /** First env key (for backward compatibility) */
        public String getEnvKeyName() { return envKeyNames[0]; }
    }

    public static void init() {
        // Preload configuration
        AiConfig.load();

        // Install Reactor context propagation so RequestContext/MDC survive thread switches
        LoggingContextPropagation.install();

        // Auto-detect available AI provider
        AiProvider selectedProvider = detectAvailableProvider();
        ChatModel model = createChatModel(selectedProvider);

        if (model == null) {
            AusukaAiMod.LOGGER.warn("Failed to initialize AI model; AI features will be disabled. Provide API keys via environment variables, JVM args, or config file.");
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

        // Init a single-thread scheduler for timeouts
        if (AI_SCHEDULER == null || AI_SCHEDULER.isShutdown()) {
            AI_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r);
                t.setName("ausuka-ai-scheduler");
                t.setDaemon(true);
                return t;
            });
        }

        // Initialize persistence layer (schema, etc.) and conversation memory system
        MyBatisSupport.init();
        conversationMemory = new ConversationMemorySystem();
        
        // ModAdminSystem requires MinecraftServer; will be lazily initialized on first access

        if (AIClient != null) {
            AusukaAiMod.LOGGER.info("AI runtime initialized, provider: {}", selectedProvider.getName());
        } else {
            AusukaAiMod.LOGGER.info("AI runtime initialized (AI disabled)");
        }
    }
    
    /**
     * Auto-detect available AI provider
     * Check environment variables by priority
     */
    private static AiProvider detectAvailableProvider() {
        // Respect explicit configuration first
        String preferred = AiConfig.getPreferredProvider();
        if (preferred != null) {
            for (AiProvider p : AiProvider.values()) {
                if (p.getName().equalsIgnoreCase(preferred)) {
                    AusukaAiMod.LOGGER.info("Configured AI provider: {}", p.getName());
                    return p;
                }
            }
            AusukaAiMod.LOGGER.warn("Unknown AI_PROVIDER='{}', falling back to auto-detection", preferred);
        }

        // Check in priority order
        for (AiProvider provider : AiProvider.values()) {
            for (String k : provider.getEnvKeyNames()) {
                String apiKey = AiConfig.get(k);
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    AusukaAiMod.LOGGER.info("Detected {} API key ({}); using this provider", provider.getName(), k);
                    return provider;
                }
            }
        }

        AusukaAiMod.LOGGER.warn("No AI provider API key detected!");
        return AiProvider.DEEPSEEK; // default fallback
    }
    
    /**
     * Create ChatModel based on provider type
     */
    private static ChatModel createChatModel(AiProvider provider) {
        String apiKey = null;
        for (String k : provider.getEnvKeyNames()) {
            apiKey = AiConfig.get(k);
            if (apiKey != null && !apiKey.isBlank()) break;
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            AusukaAiMod.LOGGER.warn("API key for provider {} is not set", provider.getName());
            return null;
        }
        
        try {
            switch (provider) {
                case DEEPSEEK:
                    return createDeepSeekModel(apiKey);
                    
                case OPENAI:
                    return createOpenAiModel(apiKey);
                    
                case CLAUDE:
                    return createClaudeModel(apiKey);
                    
                default:
                    AusukaAiMod.LOGGER.warn("Unknown AI provider: {}", provider.getName());
                    return null;
            }
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Failed to create {} model", provider.getName(), e);
            return null;
        }
    }
    
    /**
     * Create DeepSeek model instance
     */
    private static ChatModel createDeepSeekModel(String apiKey) {
        String baseUrl = AiConfig.get("DEEPSEEK_BASE_URL");
        String modelName = defaultIfBlank(AiConfig.get("DEEPSEEK_MODEL"), "deepseek-chat");

        var builder = DeepSeekApi.builder().apiKey(apiKey);
        if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(baseUrl);
        var api = builder.build();

        var options = DeepSeekChatOptions.builder()
                .model(modelName)
                .temperature(0.7)
                .build();

        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(options)
                .build();
    }

    /** Create OpenAI model instance */
    private static ChatModel createOpenAiModel(String apiKey) {
        String baseUrl = AiConfig.get("OPENAI_BASE_URL");
        String modelName = defaultIfBlank(AiConfig.get("OPENAI_MODEL"), "gpt-4o-mini");

        var builder = OpenAiApi.builder().apiKey(apiKey);
        if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(baseUrl);
        var api = builder.build();

        var options = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(0.7)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    /** Create Claude (Anthropic) model instance */
    private static ChatModel createClaudeModel(String apiKey) {
        String baseUrl = AiConfig.get("CLAUDE_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = AiConfig.get("ANTHROPIC_BASE_URL");
        String modelName = defaultIfBlank(AiConfig.get("CLAUDE_MODEL"), "claude-3-haiku-20240307");

        var builder = AnthropicApi.builder().apiKey(apiKey);
        if (baseUrl != null && !baseUrl.isBlank()) builder.baseUrl(baseUrl);
        var api = builder.build();

        var options = AnthropicChatOptions.builder()
                .model(modelName)
                .temperature(0.7)
                .build();

        return AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options)
                .build();
    }

    private static String defaultIfBlank(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }
    
    /**
     * Get conversation memory system
     */
    public static ConversationMemorySystem getConversationMemory() {
        return conversationMemory;
    }
    
    /**
     * Get MOD admin system - lazy initialization
     */
    public static ModAdminSystem getModAdminSystem() {
        return modAdminSystem;
    }
    
    /**
     * Initialize MOD admin system (requires server instance)
     */
    public static void initModAdminSystem(MinecraftServer server) {
        if (modAdminSystem == null) {
            try {
                modAdminSystem = new ModAdminSystem(server);
                AusukaAiMod.LOGGER.info("MOD admin system initialized");
            } catch (Exception e) {
                AusukaAiMod.LOGGER.error("Failed to initialize MOD admin system", e);
            }
        }
    }
    
    /**
     * Shutdown AI runtime
     */
    public static void shutdown() {
        if (AI_EXECUTOR != null) {
            AI_EXECUTOR.shutdownNow();
            AI_EXECUTOR = null;
        }
        if (AI_SCHEDULER != null) {
            AI_SCHEDULER.shutdownNow();
            AI_SCHEDULER = null;
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
     * Ready status (whether AI Client is initialized)
     */
    public static boolean isReady() {
        return AIClient != null;
    }

    /**
     * Scheduler accessor for timeout tasks.
     */
    public static ScheduledExecutorService scheduler() {
        return AI_SCHEDULER;
    }
}
