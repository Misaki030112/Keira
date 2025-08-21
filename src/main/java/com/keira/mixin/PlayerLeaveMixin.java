package com.keira.mixin;

import com.keira.KeiraAiMod;
import com.keira.ai.AiRuntime;
import com.keira.command.core.AiServices;
import com.keira.util.PlayerLanguageCache;
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
            //Clear AI chat status (memory state)
            if (AiServices.sessions().isInChat(playerName)) {
                AiServices.sessions().exit(playerName);
                KeiraAiMod.LOGGER.info("Cleaned up AI chat session state for player {}", playerName);
            }
            //Clear the session cache in the conversation memory system
            AiRuntime.getConversationMemory().clearSession(playerName);

            // Remove the language cache
            PlayerLanguageCache.remove(player);
        } catch (Exception e) {
            KeiraAiMod.LOGGER.warn("Player offline clearing session status failed: " + e.getMessage());
        }
    }
}
