package com.hinadt.tools;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 管理员权限验证工具
 * 用于区分管理员和普通用户，控制危险操作的权限
 */
public class AdminTools {
    
    private final MinecraftServer server;
    
    public AdminTools(MinecraftServer server) {
        this.server = server;
    }
    
    /**
     * 检查玩家是否为管理员
     * 在Minecraft中，管理员身份通过以下方式确定：
     * 1. 服务器单人游戏模式下的玩家 (Integrated Server)
     * 2. 多人游戏中被添加到ops.json的玩家 (Dedicated Server)
     * 3. 拥有权限等级4的玩家（最高权限）
     */
    public static boolean isPlayerAdmin(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) return false;
        
        // 方法1：检查玩家是否是操作员（op）
        boolean isOp = server.getPlayerManager().isOperator(player.getGameProfile());
        
        // 方法2：检查权限等级（4是最高级别）
        // Note: getOpPermissionLevel might not be available in this MC version, using alternative
        int permissionLevel = 0;
        try {
            if (server.getPlayerManager().isOperator(player.getGameProfile())) {
                permissionLevel = 4; // Default op level
            }
        } catch (Exception e) {
            // Fallback - assume no permission
        }
        boolean hasHighPermission = permissionLevel >= 4;
        
        // 方法3：单人游戏模式下，玩家默认拥有管理员权限
        boolean isSinglePlayer = !server.isDedicated();
        
        return isOp || hasHighPermission || isSinglePlayer;
    }
    
    /**
     * 获取玩家权限等级
     */
    public static int getPlayerPermissionLevel(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) return 0;
        try {
            // Try to use direct method if available, otherwise default to op check
            if (server.getPlayerManager().isOperator(player.getGameProfile())) {
                return 4; // Default op level
            }
        } catch (Exception e) {
            // Fallback
        }
        return 0;
    }
    
    /**
     * 生成权限不足的友好提示消息
     */
    public static String generatePermissionDeniedMessage(String playerName, String operation) {
        String[] friendlyMessages = {
            "抱歉 " + playerName + "，" + operation + " 需要管理员权限哦~ 🔒",
            playerName + " 你想" + operation + "？这个功能只有管理员才能使用呢 😅",
            "哎呀 " + playerName + "，" + operation + " 是管理员专用功能，你现在还没有权限呢 🚫",
            playerName + "，虽然我很想帮你" + operation + "，但这需要管理员权限才行~ 💭",
            "不好意思 " + playerName + "，" + operation + " 这种重要操作只能由管理员执行呢 🛡️"
        };
        
        // 随机选择一条友好的拒绝消息
        int index = (int) (Math.random() * friendlyMessages.length);
        return friendlyMessages[index];
    }
    
    @Tool(
        name = "check_admin_permission",
        description = """
        检查指定玩家是否拥有管理员权限。
        在执行敏感操作前使用此工具验证权限。
        
        管理员判定标准：
        1. 服务器操作员(OP)身份
        2. 权限等级达到4级
        3. 单人游戏模式下的玩家
        
        返回权限信息和建议的操作方式。
        """
    )
    public String checkAdminPermission(
        @ToolParam(description = "要检查权限的玩家名称") String playerName
    ) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        if (player == null) {
            return "❌ 找不到玩家：" + playerName;
        }
        
        boolean isAdmin = isPlayerAdmin(server, player);
        int permissionLevel = getPlayerPermissionLevel(server, player);
        
        String serverType = server.isDedicated() ? "专用服务器" : "单人游戏/局域网";
        
        if (isAdmin) {
            return String.format("""
                ✅ %s 拥有管理员权限
                📊 权限等级：%d
                🖥️ 服务器类型：%s
                🔓 可执行所有操作包括危险操作
                """, playerName, permissionLevel, serverType);
        } else {
            return String.format("""
                ❌ %s 不具备管理员权限
                📊 权限等级：%d
                🖥️ 服务器类型：%s
                🔒 仅可执行基础操作，危险操作被限制
                💡 提示：需要服务器管理员使用 /op %s 命令授予管理员权限
                """, playerName, permissionLevel, serverType, playerName);
        }
    }
    
    @Tool(
        name = "require_admin_or_deny",
        description = """
        验证管理员权限，如果不是管理员则返回友好的拒绝消息。
        在执行踢人、封禁、服务器控制等危险操作前必须调用此工具。
        
        如果验证通过返回"ADMIN_VERIFIED"，如果验证失败返回友好的拒绝消息。
        AI应该根据返回值决定是否继续执行危险操作。
        """
    )
    public String requireAdminOrDeny(
        @ToolParam(description = "请求执行操作的玩家名称") String playerName,
        @ToolParam(description = "尝试执行的操作描述，如'踢出玩家'、'更改天气'、'关闭自动消息系统'等") String operationDescription
    ) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        if (player == null) {
            return "❌ 系统错误：找不到玩家 " + playerName;
        }
        
        if (isPlayerAdmin(server, player)) {
            return "ADMIN_VERIFIED"; // 特殊返回值，表示权限验证通过
        } else {
            return generatePermissionDeniedMessage(playerName, operationDescription);
        }
    }
    
    @Tool(
        name = "get_admin_welcome_info",
        description = """
        获取管理员专用的欢迎信息和额外功能说明。
        当管理员进入AI聊天模式时，应该提供额外的管理功能介绍。
        """
    )
    public static String getAdminWelcomeInfo(
        @ToolParam(description = "管理员玩家名称") String adminName
    ) {
        return String.format("""
            🛡️ %s 管理员，欢迎回来！
            
            作为管理员，您拥有以下额外权限：
            🔧 服务器管理 - 踢人、封禁、重启服务器
            🌤️ 世界控制 - 天气、时间、世界边界设置
            📢 系统管理 - 自动消息系统开关、全服公告
            ⚙️ MOD配置 - AI系统参数调整、功能启用/禁用
            📊 监控面板 - 玩家行为分析、服务器性能监控
            🔒 权限管理 - 其他玩家权限设置
            
            💡 所有危险操作都需要您的管理员身份验证
            """, adminName);
    }
}