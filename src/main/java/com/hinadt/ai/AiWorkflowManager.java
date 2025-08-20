package com.hinadt.ai;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.context.PlayerContextBuilder;
import com.hinadt.ai.prompt.PromptComposer;
import com.hinadt.ai.tools.ToolRegistry;
import com.hinadt.observability.RequestContext;
import com.hinadt.tools.AdminTools;
import com.hinadt.util.PlayerLanguageCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.concurrent.CancellationException;


public class AiWorkflowManager {

    private final MinecraftServer server;
    private final ToolRegistry tools;
    private final ConversationMemorySystem memorySystem;
    private final PlayerContextBuilder contextBuilder = new PlayerContextBuilder();
    private final PromptComposer promptComposer = new PromptComposer();

    public AiWorkflowManager(MinecraftServer server) {
        this.server = server;
        this.tools = new ToolRegistry(server);
        this.memorySystem = AiRuntime.getConversationMemory();
    }

    /**
     * Process a player's message with messageId for tracing.
     */
    public String processPlayerMessage(ServerPlayerEntity player, String message, String messageId) {
        try {
            RequestContext.setMessageId(messageId);
            if (!AiRuntime.isReady()) {
                return "⚠️ AI is not configured or unavailable. Configure API key and retry.";
            }
            // Honor cooperative cancellation early
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("AI task cancelled before start");
            }
            String playerName = player.getName().getString();

            // 记录用户消息
            memorySystem.saveUserMessage(playerName, message);
            AusukaAiMod.LOGGER.debug("{} [workflow] Save user message player={}, len={}", RequestContext.midTag(), playerName, message.length());

            // 上下文
            String detailedContext = contextBuilder.build(player);
            String conversationContext = memorySystem.getConversationContext(playerName);
            boolean isAdmin = AdminTools.isPlayerAdmin(server, player);
            String permissionContext = isAdmin ?
                    "Player has admin privileges; risky operations permitted." :
                    "Player is a regular user; risky operations require permission and will be denied.";

            // 记忆与服务器概览
            String memoryContext = getPlayerMemoryContext(playerName);
            String serverContext = buildServerContext();
            String toolAvailability = buildToolAvailability(isAdmin);

            // Compose system prompt; reply language from cached client locale (default en_us)
            String clientLocale = PlayerLanguageCache.code(player);

            String systemPrompt = promptComposer.composeSystemPrompt(
                    player,
                    detailedContext,
                    conversationContext,
                    permissionContext,
                    isAdmin,
                    memoryContext,
                    serverContext,
                    toolAvailability,
                    clientLocale
            );

            // Server-side logging: record latency to diagnose slow requests
            long start = System.currentTimeMillis();
            AusukaAiMod.LOGGER.info("{} AI request start: player={}, msg='{}'",RequestContext.midTag() , playerName, message);
            AusukaAiMod.LOGGER.debug("{} [workflow] Context ready before AI call  sysPrompt='''\n{}\n'''", RequestContext.midTag(), systemPrompt);

            // Check cancellation before making a potentially long blocking network call
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("AI task cancelled before network call");
            }

            String aiResponse = AiRuntime.AIClient
                    .prompt()
                    .system(systemPrompt)
                    .user(message)
                    .tools(
                            tools.itemSearchTool,
                            tools.giveItemTool,
                            tools.enchantItemTool,
                            tools.teleportTools,
                            tools.memoryTools,
                            tools.weatherTools,
                            tools.statusEffectTools,
                            tools.playerStatsTools,
                            tools.worldAnalysisTools,
                            tools.adminTools
                    )
                    .call()
                    .content();

            long cost = System.currentTimeMillis() - start;
            if (cost > 15000) {
                AusukaAiMod.LOGGER.warn("AI request done (slow): player={}, cost={}ms", playerName, cost);
            } else {
                AusukaAiMod.LOGGER.info("AI request done: player={}, cost={}ms", playerName, cost);
            }
            int respLen = aiResponse == null ? 0 : aiResponse.length();
            AusukaAiMod.LOGGER.debug("{} [workflow] AI response to player={}, len={}, aiResponse='{}'",playerName, RequestContext.midTag(), respLen, aiResponse);

            memorySystem.saveAiResponse(playerName, aiResponse);
            return aiResponse;

        } catch (CancellationException e) {
            // Propagate cooperative cancellation without noisy logs
            throw e;
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Error processing player message: " + e.getMessage(), e);
            return "😅 Sorry, I hit a technical issue handling your request. Please try again later or rephrase.";
        } finally {
            RequestContext.clear();
        }
    }

    /**
     * Process a single-turn message without using or updating conversation history.
     * - No conversation context
     * - Does not save user/AI messages to memory
     * - Still respects permissions, tools availability, and reply language
     */
    public String processSingleTurnMessage(ServerPlayerEntity player, String message, String messageId) {
        try {
            RequestContext.setMessageId(messageId);
            if (!AiRuntime.isReady()) {
                return "⚠️ AI is not configured or unavailable. Configure API key and retry.";
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("AI one-shot cancelled before start");
            }
            String playerName = player.getName().getString();

            // Context (no conversation history)
            String detailedContext = contextBuilder.build(player);
            String conversationContext = ""; // explicitly empty for one-shot
            boolean isAdmin = AdminTools.isPlayerAdmin(server, player);
            String permissionContext = isAdmin ?
                    "Player has admin privileges; risky operations permitted." :
                    "Player is a regular user; risky operations require permission and will be denied.";

            // No player memory context for one-shot; keep server/tool context
            String memoryContext = "(memory disabled for one-shot)";
            String serverContext = buildServerContext();
            String toolAvailability = buildToolAvailability(isAdmin);

            String clientLocale = PlayerLanguageCache.code(player);

            String systemPrompt = promptComposer.composeSystemPrompt(
                    player,
                    detailedContext,
                    conversationContext,
                    permissionContext,
                    isAdmin,
                    memoryContext,
                    serverContext,
                    toolAvailability,
                    clientLocale
            );

            long start = System.currentTimeMillis();
            AusukaAiMod.LOGGER.info("AI one-shot start: player={}, msg='{}'", playerName, message);
            AusukaAiMod.LOGGER.debug("{} [one-shot] Context ready sysPromptLen={}", RequestContext.midTag(), systemPrompt.length());

            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("AI one-shot cancelled before network call");
            }

            String aiResponse = AiRuntime.AIClient
                    .prompt()
                    .system(systemPrompt)
                    .user(message)
                    .tools(
                            tools.itemSearchTool,
                            tools.giveItemTool,
                            tools.enchantItemTool,
                            tools.teleportTools,
                            tools.memoryTools,
                            tools.weatherTools,
                            tools.statusEffectTools,
                            tools.playerStatsTools,
                            tools.worldAnalysisTools,
                            tools.adminTools
                    )
                    .call()
                    .content();

            long cost = System.currentTimeMillis() - start;
            if (cost > 8000) {
                AusukaAiMod.LOGGER.warn("AI one-shot done (slow): player={}, cost={}ms", playerName, cost);
            } else {
                AusukaAiMod.LOGGER.info("AI one-shot done: player={}, cost={}ms", playerName, cost);
            }
            int respLen = aiResponse == null ? 0 : aiResponse.length();
            String preview = aiResponse == null ? "" : aiResponse.substring(0, Math.min(180, aiResponse.length())).replaceAll("\n", " ");
            AusukaAiMod.LOGGER.debug("{} [one-shot] AI response len={}, preview='{}'", RequestContext.midTag(), respLen, preview);

            return aiResponse;

        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Error processing one-shot message: " + e.getMessage(), e);
            return "😅 Sorry, I hit a technical issue handling your request. Please try again later or rephrase.";
        } finally {
            RequestContext.clear();
        }
    }

    private String buildToolAvailability(boolean isAdmin) {
        StringBuilder sb = new StringBuilder();
        sb.append("- Items, teleport, weather, world analysis, player stats: available\n");
        if (isAdmin) {
            sb.append("- Admin tools: enabled (kick/ban/force-teleport/jail, etc.)\n");
        } else {
            sb.append("- Admin tools: restricted; risky operations require permission and will be denied\n");
        }
        return sb.toString();
    }

    private String getPlayerMemoryContext(String playerName) {
        try {
            String locationMemory = tools.memoryTools.listSavedLocations(playerName);
            StringBuilder memory = new StringBuilder();
            memory.append("**Player Memory**:\n");
            memory.append(locationMemory).append("\n");
            memory.append("- Preference memory is in development; only location memory is supported\n");
            return memory.toString();
        } catch (Exception e) {
            return "**Player Memory**:\n- Memory system unavailable\n";
        }
    }

    private String buildServerContext() {
        StringBuilder context = new StringBuilder();
        context.append("**Server Status**:\n");
        var playerManager = server.getPlayerManager();
        int onlineCount = playerManager.getPlayerList().size();
        context.append(String.format("- Online players: %d\n", onlineCount));
        if (onlineCount > 0) {
            context.append("- Players online: ");
            playerManager.getPlayerList().forEach(p ->
                    context.append(p.getName().getString()).append(" "));
            context.append("\n");
        }
        context.append(String.format("- Server type: %s\n", server.isDedicated() ? "Dedicated" : "Singleplayer/LAN"));
        return context.toString();
    }
    
    
}
