package com.keira;

import com.keira.ai.AiConfig;
import com.keira.ai.AiRuntime;
import com.keira.chat.AiChatSystem;
import com.keira.chat.IntelligentAutoMessageSystem;
import com.keira.command.AiCommandRegistry;
import com.keira.command.core.AiServices;
import com.keira.util.Messages;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeiraAiMod implements ModInitializer {
    public static final String MOD_ID = "keira";

    // Logger named by mod id for clarity in logs
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("🤖 Keira Mod is loading...");

        // Register commands early so Brigadier has them when server builds the tree
        try {
            AiCommandRegistry.initialize();
            LOGGER.info("✅ Command registration callback attached");
        } catch (Exception e) {
            LOGGER.error("❌ Failed to initialize command registration", e);
        }

        // Initialize AI when server starts
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("🚀 Server started, initializing AI-driven system...");
            try {
                // Attach server to config first so server.properties participates in resolution
                AiConfig.attachServer(server);
                LOGGER.debug("🔧 Attached server to AI config for property resolution");

                // Initialize AI runtime (reads config with server.properties available)
                AiRuntime.init();
                LOGGER.info("✅ AI runtime initialized");

                // Initialize service container and AI chat system (listeners, etc.)
                AiServices.initialize(server);
                AiChatSystem.initialize();
                LOGGER.info("✅ AI chat system and command registration completed");

                // Initialize intelligent auto message system
                IntelligentAutoMessageSystem.initialize(server);
                LOGGER.info("✅ Intelligent auto message system initialized");

                LOGGER.info("🎉 Keira Mod system initialization completed!");

                // Send a startup welcome message
                server.execute(() ->
                    Messages.broadcast(
                        server,
                        Text.translatable("keira.server.online")
                    )
                );

            } catch (Exception e) {
                LOGGER.error("❌ Failed to initialize AI system: " + e.getMessage(), e);
            }
        });

        // Cleanup when server stops
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LOGGER.info("🔄 Server stopping, cleaning up AI system...");
            IntelligentAutoMessageSystem.shutdown();
            AiRuntime.shutdown();
            LOGGER.info("✅ AI system cleanup completed");
        });

        LOGGER.info("✨ Keira Mod loaded!");
    }
}
