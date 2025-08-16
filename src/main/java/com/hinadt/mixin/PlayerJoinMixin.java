package com.hinadt.mixin;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.persistence.PlayerTelemetryRecorder;
import net.minecraft.network.ClientConnection;
import com.hinadt.util.Messages;
import com.hinadt.util.PlayerLanguageCache;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerJoinMixin {
    
    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void onPlayerJoin(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
        // 记录玩家加入时的客户端信息（语言、IP等）
        try {
            PlayerTelemetryRecorder.recordJoin(player, connection, clientData);
        } catch (Exception e) {
            AusukaAiMod.LOGGER.warn("记录玩家连接信息出错: " + e.getMessage());
        }

        // 缓存玩家语言（仅在加入/设置变更时更新）
        try {
            PlayerLanguageCache.update(player);
        } catch (Exception e) {
            AusukaAiMod.LOGGER.warn("缓存玩家语言失败: " + e.getMessage());
        }
        // 延迟发送AI生成的欢迎消息
        player.getServer().execute(() -> {
            try {
                Thread.sleep(2000); // 等待2秒确保玩家完全加载
                
                // 发送AI生成的欢迎消息（AI 提示为英文，回复语言依据缓存）
                generateAiWelcomeMessage(player);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    private void generateAiWelcomeMessage(ServerPlayerEntity player) {
        try {
            String playerName = player.getName().getString();
            String responseLocale = PlayerLanguageCache.code(player);
            String welcomePrompt = String.format("""
                You are Ausuka.ai, the in-server assistant.
                A player named %s just joined. Generate a short, friendly welcome message.

                Include:
                1) Warm personalized greeting
                2) Brief intro that you are Ausuka.ai
                3) Core features overview (items, smart teleport, building tips, weather, etc.)
                4) How to start (/ai help)
                5) Encouraging closing

                Constraints:
                - Keep it concise (<= 150 chars)
                - Use appropriate emojis
                - Output MUST be in player's client language: %s (fallback en_us)
                - Do not include system-only instructions in the output
                """, playerName, responseLocale);
            
            String welcomeMessage;
            if (!AiRuntime.isReady()) {
                throw new IllegalStateException("AI未配置");
            }

            welcomeMessage = AiRuntime.AIClient
                    .prompt()
                    .user(welcomePrompt)
                    .call()
                    .content();
            
            // 发送AI生成的欢迎消息
            Messages.to(player, Text.translatable("ausuka.ai.reply", welcomeMessage));
            Messages.to(player, Text.translatable("ausuka.welcome.help"));
            
            AusukaAiMod.LOGGER.info("已为玩家 {} 发送AI生成的欢迎消息", playerName);
            
        } catch (Exception e) {
            // 如果AI生成失败，使用多语言兜底
            Messages.to(player, Text.translatable("ausuka.welcome.fallback", player.getName().getString()));
            
            AusukaAiMod.LOGGER.warn("AI欢迎消息生成失败，使用备用消息: " + e.getMessage());
        }
    }
}
