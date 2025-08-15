package com.hinadt.mixin;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerJoinMixin {
    
    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void onPlayerJoin(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        // 延迟发送AI生成的欢迎消息
        player.getServer().execute(() -> {
            try {
                Thread.sleep(2000); // 等待2秒确保玩家完全加载
                
                // 发送AI生成的欢迎消息
                generateAiWelcomeMessage(player);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    private void generateAiWelcomeMessage(ServerPlayerEntity player) {
        try {
            String playerName = player.getName().getString();
            String welcomePrompt = String.format("""
                玩家 %s 刚刚加入服务器。请生成一个友好的欢迎消息，介绍本AI模组的主要功能。
                
                要包含：
                1. 热情的个性化欢迎
                2. 简要介绍你是AI助手 Ausuka.ai
                3. 核心功能概览（物品管理、智能传送、建筑助手、天气控制等）
                4. 如何开始使用（输入 /ai help 查看命令）
                5. 鼓励性的结语
                
                要求：
                - 简洁友好，不超过150字
                - 用中文
                - 包含合适的emoji
                - 让玩家感到兴奋和好奇
                """, playerName);
            
            String welcomeMessage = AiRuntime.AIClient
                .prompt()
                .user(welcomePrompt)
                .call()
                .content();
            
            // 发送AI生成的欢迎消息
            player.sendMessage(Text.of("§b🤖 [Ausuka.ai] §f" + welcomeMessage));
            player.sendMessage(Text.of("§e💡 输入 §a/ai help §e查看完整功能列表！"));
            
            AusukaAiMod.LOGGER.info("已为玩家 {} 发送AI生成的欢迎消息", playerName);
            
        } catch (Exception e) {
            // 如果AI生成失败，使用备用欢迎消息
            String fallbackMessage = String.format("""
                🤖 欢迎加入服务器，%s！
                
                我是AI助手 Ausuka.ai，可以帮助你：
                • 📦 智能物品管理
                • 🚀 记忆式传送系统  
                • 🏗️ 建筑设计建议
                • 🌤️ 天气时间控制
                • ❤️ 玩家状态管理
                
                输入 /ai help 开始体验AI驱动的游戏助手！
                """, player.getName().getString());
            
            player.sendMessage(Text.of("§b🤖 [Ausuka.ai] §f" + fallbackMessage));
            
            AusukaAiMod.LOGGER.warn("AI欢迎消息生成失败，使用备用消息: " + e.getMessage());
        }
    }
}
