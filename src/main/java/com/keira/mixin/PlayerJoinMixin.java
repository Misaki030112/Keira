package com.keira.mixin;

import com.keira.KeiraAiMod;
import com.keira.ai.AiRuntime;
import com.keira.persistence.PlayerTelemetryRecorder;
import com.keira.util.Messages;
import com.keira.util.PlayerLanguageCache;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(PlayerManager.class)
public class PlayerJoinMixin {
    
    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void onPlayerJoin(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {

        PlayerTelemetryRecorder.recordJoin(player,connection,clientData);
        // Delay sending the AI generated welcome message
        Objects.requireNonNull(player.getServer()).execute(() -> {
            try {
                Thread.sleep(2000); // Wait 2 seconds to ensure the player is fully loaded
                // Send an AI-generated welcome message (AI prompts are in English, and the reply language is based on the cache)
                generateAiWelcomeMessage(player);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    @Unique
    private void generateAiWelcomeMessage(ServerPlayerEntity player) {
        try {
            String playerName = player.getName().getString();
            String responseLocale = PlayerLanguageCache.code(player);
            String welcomePrompt = String.format("""
                You are Keira, the in-server assistant.
                A player named %s just joined. Generate a short, friendly welcome message.

                Include:
                1) Warm personalized greeting
                2) Brief intro that you are Keira
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
                throw new IllegalStateException("AI not configured");
            }

            KeiraAiMod.LOGGER.debug("the prompt for AI generated player {} welcome message, {}", playerName, welcomePrompt);

            welcomeMessage = AiRuntime.AIClient
                    .prompt()
                    .user(welcomePrompt)
                    .call()
                    .content();
            
            // 发送AI生成的欢迎消息
            Messages.to(player, Text.translatable("keira.ai.reply", welcomeMessage));
            Messages.to(player, Text.translatable("keira.welcome.help"));
            
            KeiraAiMod.LOGGER.info("Sent AI generated welcome message for player {}", playerName);
            
        } catch (Exception e) {
            // If AI generation fails, use multi-language as a fallback
            Messages.to(player, Text.translatable("keira.welcome.fallback", player.getName().getString()));
            
            KeiraAiMod.LOGGER.warn("AI welcome message generation failed, using fallback message: " + e.getMessage());
        }
    }
}
