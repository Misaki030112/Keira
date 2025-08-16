package com.hinadt.mixin;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.command.core.AiServices;
import com.hinadt.util.PlayerLanguageCache;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerLeaveMixin {

    @Inject(method = "remove", at = @At("TAIL"))
    private void onPlayerLeave(ServerPlayerEntity player, CallbackInfo ci) {
        try {
            String playerName = player.getName().getString();
            // 清理AI聊天状态（内存态）
            if (AiServices.sessions().isInChat(playerName)) {
                AiServices.sessions().exit(playerName);
                AusukaAiMod.LOGGER.info("已清理玩家 {} 的AI聊天会话状态", playerName);
            }
            // 清理对话记忆系统中的会话缓存
            AiRuntime.getConversationMemory().clearSession(playerName);

            // 移除语言缓存
            PlayerLanguageCache.remove(player);
        } catch (Exception e) {
            AusukaAiMod.LOGGER.warn("玩家下线清理会话状态失败: " + e.getMessage());
        }
    }
}
