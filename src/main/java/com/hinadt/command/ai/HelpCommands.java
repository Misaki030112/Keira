package com.hinadt.command.ai;

import com.hinadt.command.core.Permissions;
import com.hinadt.ai.ModAdminSystem;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class HelpCommands {
    private HelpCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return LiteralArgumentBuilder.<ServerCommandSource>literal("help")
            .executes(HelpCommands::executeHelp);
    }

    public static int executeHelp(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        sendHelp(player);
        return 1;
    }

    public static void sendHelp(ServerPlayerEntity player) {
        ModAdminSystem.PermissionLevel perm = Permissions.getPlayerPermission(player);

        // 主命令帮助
        sendMultiline(player, String.join("\n",
            "§b=== Ausuka.ai 助手命令帮助 ===§r",
            "§a/ai help§r - 查看命令帮助",
            "§a/ai status§r - 查看AI聊天状态",
            "§a/ai chat§r - 进入AI对话模式",
            "§a/ai say <消息>§r - 单次向AI提问",
            "§a/ai exit§r - 退出AI对话模式",
            "§a/ai new§r - 开始新的对话会话",
            "§7使用 /ai <子命令> 获取详细帮助§r"
        ));

        player.sendMessage(Text.of(""));
        player.sendMessage(Text.of("§b=== AI聊天模式功能 ==="));
        player.sendMessage(Text.of("§a🎒 智能物品管理 §7- \"我想要钻石剑\" / \"帮我整理背包\""));
        player.sendMessage(Text.of("§a🚀 智能传送系统 §7- \"带我回家\" / \"记住这里是我的农场\""));
        player.sendMessage(Text.of("§a🧠 AI记忆系统 §7- \"记住我喜欢现代建筑风格\""));
        player.sendMessage(Text.of("§a🏗️ 建筑助手 §7- \"帮我设计一个城堡\""));
        player.sendMessage(Text.of("§a🌤️ 环境控制 §7- \"我想要晴天\" / \"设置为白天\""));
        player.sendMessage(Text.of("§a❤️ 玩家服务 §7- \"治疗我\" / \"查看玩家信息\""));
        player.sendMessage(Text.of("§a🔍 环境分析 §7- \"分析周围环境\" / \"寻找钻石\""));
        player.sendMessage(Text.of("§a💬 对话记忆 §7- AI会记住整个对话过程和上下文"));

        if (perm.hasPermission(ModAdminSystem.PermissionLevel.MOD_ADMIN)) {
            player.sendMessage(Text.of(""));
            sendMultiline(player, String.join("\n",
                "§c=== 管理员专用命令 ===§r",
                "§c/ai admin auto-msg toggle§r - 切换自动消息系统开关",
                "§c/ai admin auto-msg status§r - 查看自动消息系统状态",
                "§c/ai admin auto-msg personal <玩家> <on|off>§r - 为玩家开/关个性化消息"
            ));
        }

        player.sendMessage(Text.of(""));
        player.sendMessage(Text.of("§e💡 提示：在AI聊天模式中，直接说出你的需求，AI会自动理解并提供帮助！"));
        player.sendMessage(Text.of("§e🔄 使用 /ai new 开始新对话可以清除之前的对话记忆"));
    }

    private static void sendMultiline(ServerPlayerEntity player, String text) {
        for (String line : text.split("\n")) player.sendMessage(Text.of(line));
    }
}
