package com.hinadt;

import com.hinadt.ai.AiRuntime;
import com.hinadt.chat.AiChatSystem;
import com.hinadt.util.Messages;
import com.hinadt.command.AiCommandRegistry;
import com.hinadt.command.core.AiServices;
import com.hinadt.chat.IntelligentAutoMessageSystem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.text.Text;

@SuppressWarnings("resource")
public class AusukaAiMod implements ModInitializer {
    public static final String MOD_ID = "ausuka-ai-mod";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

        LOGGER.info("🤖 Ausuka.ai Mod is loading...");

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
				// Initialize AI runtime
				AiRuntime.init();
				LOGGER.info("✅ AI runtime initialized");

                // Initialize service container and AI chat system (listeners, etc.)
                AiServices.initialize(server);
                AiChatSystem.initialize();
                LOGGER.info("✅ AI chat system and command registration completed");

                // Session state is maintained in memory only; no database cleanup required

				// Initialize intelligent auto message system
				IntelligentAutoMessageSystem.initialize(server);
				LOGGER.info("✅ Intelligent auto message system initialized");

				LOGGER.info("🎉 Ausuka.ai Mod system initialization completed!");

				// Send a startup welcome message
                server.execute(() ->
                    Messages.broadcast(
                        server,
                        Text.translatable("ausuka.server.online")
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

		LOGGER.info("✨ Ausuka.ai Mod loaded!");
	}
}
