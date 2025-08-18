package com.hinadt.command.ai;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.command.core.AiServices;
import com.hinadt.tools.AdminTools;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.hinadt.util.Messages;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("resource")
public final class ChatHelpers {
    private ChatHelpers() {}

    public static void sendAiWelcomeMessage(ServerPlayerEntity player) {
        CompletableFuture.runAsync(() -> {
            try {
                String playerName = player.getName().getString();
                boolean isAdmin = AdminTools.isPlayerAdmin(AiServices.server(), player);
                String responseLocale = com.hinadt.util.PlayerLanguageCache.code(player);

                String toolCapabilities = """
                ## Core Capabilities ##

                🎒 Items
                • Smart search and recommendations
                • Precise give with counts and attributes
                • Inventory analysis and tidy-up

                🚀 Teleport
                • Named location memory ("remember this is home")
                • Precise XYZ and cross-dimension teleport
                • Natural language targets ("go underground", "to the sky")

                🧠 Memory
                • Locations and preferences
                • Personalized suggestions based on history

                🌤️ Environment (needs permission)
                • Weather/time control
                • World info overview

                ❤️ Player Services
                • Heal, restore, clear effects
                • Social helpers, player info
                • Stats and progress insights

                🔍 Analysis
                • Biome/resource/safety scanning
                • Resource finding and building tips
                """;

                String adminInfo = isAdmin ? AdminTools.getAdminWelcomeInfo(playerName) : "";

                String welcomePrompt = String.format("""
                You are Ausuka.ai. Create a short, friendly welcome for player %s who just entered AI chat mode.

                Player permissions: %s

                Your capabilities:
                %s

                Extra admin info:
                %s

                Requirements:
                1) Warm tone with emojis
                2) Briefly introduce core features
                3) Encourage natural language usage
                4) Address the player by name
                5) Mention admin-only features if applicable
                6) 150–200 characters

                Output language MUST be: %s (fallback en_us).
                Do not include system-only instructions in the output.
                """,
                    playerName,
                    isAdmin ? "admin user with full privileges" : "regular user; some features need permission",
                    toolCapabilities,
                    adminInfo,
                    responseLocale
                );

                String welcome = AiRuntime.AIClient
                    .prompt()
                    .user(welcomePrompt)
                    .call()
                    .content();

                AiServices.server().execute(() -> Messages.to(player, Text.translatable("ausuka.ai.reply", welcome)));

            } catch (Exception e) {
                AusukaAiMod.LOGGER.error("Error generating AI welcome message", e);
                AiServices.server().execute(() -> Messages.to(player, Text.translatable("aim.chat.welcome.fallback", player.getName().getString())));
            }
        });
    }
}
