package com.hinadt.ai;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.context.PlayerContextBuilder;
import com.hinadt.ai.prompt.PromptComposer;
import com.hinadt.ai.tools.ToolRegistry;
import com.hinadt.tools.AdminTools;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 重构后的 AI 工作流管理器
 * - 职责清晰：上下文构建、提示词组装、工具注册分别由独立类负责
 * - 便于测试与维护
 */
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

    public String processPlayerMessage(ServerPlayerEntity player, String message) {
        try {
            String playerName = player.getName().getString();

            // 记录用户消息
            memorySystem.saveUserMessage(playerName, message);

            // 上下文
            String detailedContext = contextBuilder.build(player);
            String conversationContext = memorySystem.getConversationContext(playerName);
            boolean isAdmin = AdminTools.isPlayerAdmin(server, player);
            String permissionContext = isAdmin ?
                    "玩家具有管理员权限，可以执行所有操作包括危险操作。" :
                    "玩家为普通用户，危险操作需要权限验证。";

            // 记忆与服务器概览
            String memoryContext = getPlayerMemoryContext(playerName);
            String serverContext = buildServerContext();
            String toolAvailability = buildToolAvailability(isAdmin);

            // 提示词
            String prompt = promptComposer.compose(
                    player, message, detailedContext, conversationContext,
                    permissionContext, isAdmin, memoryContext, serverContext, toolAvailability);

            // 一次性 AI 调用 + 工具注册
            String aiResponse = AiRuntime.AIClient
                    .prompt()
                    .user(prompt)
                    .tools(
                            tools.mcTools,
                            tools.teleportTools,
                            tools.memoryTools,
                            tools.weatherTools,
                            tools.playerStatsTools,
                            tools.worldAnalysisTools,
                            tools.adminTools
                    )
                    .call()
                    .content();

            memorySystem.saveAiResponse(playerName, aiResponse);
            return aiResponse;

        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("处理玩家消息时出错: " + e.getMessage(), e);
            return "😅 抱歉，我在处理你的请求时遇到了一些技术问题。请稍后再试，或者尝试用不同的方式描述你的需求。";
        }
    }

    private String buildToolAvailability(boolean isAdmin) {
        StringBuilder sb = new StringBuilder();
        sb.append("- 物品、传送、天气、世界分析、玩家信息：可用\n");
        if (isAdmin) {
            sb.append("- 管理员工具：已启用，可执行踢人/封禁/强制传送/监禁等\n");
        } else {
            sb.append("- 管理员工具：受限，危险操作需权限验证，将被拒绝\n");
        }
        return sb.toString();
    }

    private String getPlayerMemoryContext(String playerName) {
        try {
            String locationMemory = tools.memoryTools.listSavedLocations(playerName);
            StringBuilder memory = new StringBuilder();
            memory.append("**玩家记忆信息**:\n");
            memory.append(locationMemory).append("\n");
            memory.append("- 偏好系统开发中，当前版本仅支持位置记忆\n");
            return memory.toString();
        } catch (Exception e) {
            return "**玩家记忆信息**:\n- 记忆系统暂时无法访问\n";
        }
    }

    private String buildServerContext() {
        StringBuilder context = new StringBuilder();
        context.append("**服务器状态**:\n");
        var playerManager = server.getPlayerManager();
        int onlineCount = playerManager.getPlayerList().size();
        context.append(String.format("- 在线玩家数: %d\n", onlineCount));
        if (onlineCount > 0) {
            context.append("- 在线玩家: ");
            playerManager.getPlayerList().forEach(p ->
                    context.append(p.getName().getString()).append(" "));
            context.append("\n");
        }
        context.append(String.format("- 服务器类型: %s\n", server.isDedicated() ? "专用服务器" : "单人游戏/局域网"));
        return context.toString();
    }
}
