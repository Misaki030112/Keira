package com.hinadt.command.ai;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.command.core.AiServices;
import com.hinadt.tools.AdminTools;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.hinadt.tools.Messages;

import java.util.concurrent.CompletableFuture;

public final class ChatHelpers {
    private ChatHelpers() {}

    public static void sendAiWelcomeMessage(ServerPlayerEntity player) {
        CompletableFuture.runAsync(() -> {
            try {
                String playerName = player.getName().getString();
                boolean isAdmin = AdminTools.isPlayerAdmin(AiServices.server(), player);

                String toolCapabilities = """
                ## 🎮 我的核心能力 ##

                🎒 **智能物品管理**
                • 物品搜索与智能推荐：支持模糊搜索，"我想要建房子的材料"
                • 精确物品给予：支持数量控制和特殊属性设置
                • 背包分析：帮你整理和优化背包空间

                🚀 **智能传送系统**
                • 记忆位置传送：你可以说"记住这里是我的家"，之后"带我回家"
                • 坐标精确传送：支持三维坐标和多世界传送
                • 智能位置解析：理解"带我去地下"、"送我到天空"等自然语言

                🧠 **AI记忆系统**
                • 位置记忆：保存你的重要地点和建筑
                • 偏好学习：记住你的建筑风格、材料偏好、游戏习惯
                • 个性化服务：基于你的历史互动提供定制建议

                🌤️ **环境控制** (需要合适权限)
                • 天气管理：晴天、雨天、雷暴随你心意
                • 时间控制：白天黑夜，想要什么时候就什么时候
                • 世界信息：详细的环境和状态分析

                ❤️ **玩家服务**
                • 健康管理：治疗、恢复状态、清除负面效果
                • 社交助手：私信发送、玩家信息查询
                • 统计分析：成就追踪、游戏数据分析

                🔍 **智能分析**
                • 环境扫描：分析周围生物群系、资源分布、安全状况
                • 资源寻找：帮你定位特定矿物、建筑材料
                • 建筑建议：基于环境和偏好的个性化建筑指导
                """;

                String adminInfo = isAdmin ? AdminTools.getAdminWelcomeInfo(playerName) : "";

                String welcomePrompt = String.format("""
                你需要为刚进入AI聊天模式的玩家 %s 生成一条个性化欢迎消息。

                玩家权限状态：%s

                以下是你的详细能力描述：
                %s

                %s

                请生成一条欢迎消息，要求：
                1. 热情友好，体现AI伴侣的特色
                2. 简要介绍核心功能，让玩家了解你能做什么
                3. 鼓励玩家尝试自然语言交流
                4. 个性化称呼玩家
                5. %s
                6. 长度控制在150-200字
                7. 使用中文，语调要亲切自然
                8. 可以包含适当的emoji增加亲切感

                记住：你是玩家信赖的AI伙伴 Ausuka.ai，智能、贴心、专业！
                """,
                    playerName,
                    isAdmin ? "管理员用户，拥有完整权限" : "普通用户，部分功能需要权限验证",
                    toolCapabilities,
                    adminInfo,
                    isAdmin ? "强调管理员专属功能" : "说明部分功能需要管理员权限"
                );

                String welcome = AiRuntime.AIClient
                    .prompt()
                    .user(welcomePrompt)
                    .call()
                    .content();

                AiServices.server().execute(() -> Messages.to(player, Text.of("§b[Ausuka.ai] §f" + welcome)));

            } catch (Exception e) {
                AusukaAiMod.LOGGER.error("生成AI欢迎消息时出错", e);
                AiServices.server().execute(() -> {
                    String fallbackWelcome = "🤖 你好 " + player.getName().getString() + "！我是AI助手 Ausuka.ai，" +
                        "可以帮助你管理物品、智能传送、记忆重要位置、建筑指导等。" +
                        "直接告诉我你需要什么帮助，我会智能理解并为你服务！✨";
                    Messages.to(player, Text.of("§b[Ausuka.ai] §f" + fallbackWelcome));
                });
            }
        });
    }
}
