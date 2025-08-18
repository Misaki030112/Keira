package com.hinadt.mixin;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.persistence.PlayerTelemetryRecorder;
import com.hinadt.util.Messages;
import com.hinadt.util.PlayerLanguageCache;
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
        // Record the client information when the player joins (language, IP, etc.)
        try {
            PlayerTelemetryRecorder.recordJoin(player, connection, clientData);
        } catch (Exception e) {
            AusukaAiMod.LOGGER.warn("Error recording player connection information: " + e.getMessage());
        }

        // Cache player language (only updated on join/settings change)
        try {
            PlayerLanguageCache.update(player);
        } catch (Exception e) {
            AusukaAiMod.LOGGER.warn("Failed to cache player language: " + e.getMessage());
        }
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
                throw new IllegalStateException("AI not configured");
            }

            welcomeMessage = AiRuntime.AIClient
                    .prompt()
                    .user(welcomePrompt)
                    .call()
                    .content();
            
            // 发送AI生成的欢迎消息
            Messages.to(player, Text.translatable("ausuka.ai.reply", welcomeMessage));
            Messages.to(player, Text.translatable("ausuka.welcome.help"));
            
            AusukaAiMod.LOGGER.info("Sent AI generated welcome message for player {}", playerName);
            
        } catch (Exception e) {
            // If AI generation fails, use multi-language as a fallback
            Messages.to(player, Text.translatable("ausuka.welcome.fallback", player.getName().getString()));
            
            AusukaAiMod.LOGGER.warn("AI welcome message generation failed, using fallback message: " + e.getMessage());
        }
    }
}
