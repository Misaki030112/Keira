package com.hinadt;

import com.hinadt.ai.AiRuntime;
import com.hinadt.chat.AiChatSystem;
import com.hinadt.command.AiCommandRegistry;
import com.hinadt.command.core.AiServices;
import com.hinadt.chat.IntelligentAutoMessageSystem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        LOGGER.info("🤖 Ausuka.ai Mod 正在加载中...");

		// Initialize AI when server starts
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("🚀 服务器启动，正在初始化AI驱动系统...");
			try {
				// 初始化AI运行时
				AiRuntime.init();
				LOGGER.info("✅ AI运行时初始化完成");
				
                // 初始化服务容器与AI聊天系统（监听等）
                AiServices.initialize(server);
                AiChatSystem.initialize();
                AiCommandRegistry.initialize();
                LOGGER.info("✅ AI聊天系统与命令注册完成");

                // 清理过期的会话状态（7天）
                int cleaned = com.hinadt.command.core.DatabaseAiChatSessionStore.cleanupOldEntriesHours(24 * 7);
                LOGGER.info("🧹 已清理过期聊天会话记录: {} 条", cleaned);
				
				// 初始化智能自动消息系统
				IntelligentAutoMessageSystem.initialize(server);
				LOGGER.info("✅ 智能自动消息系统初始化完成");
				
				LOGGER.info("🎉 Ausuka.ai Mod 系统初始化完成！");
				
				// 发送启动欢迎消息
				server.execute(() -> {
					server.getPlayerManager().broadcast(
						net.minecraft.text.Text.of("§b🤖 [Ausuka.ai] §a系统上线！输入 §f/ai help §a查看功能"), 
						false
					);
				});
				
			} catch (Exception e) {
				LOGGER.error("❌ AI系统初始化失败: " + e.getMessage(), e);
			}
		});

		// Cleanup when server stops
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOGGER.info("🔄 服务器停止，正在清理AI系统...");
			IntelligentAutoMessageSystem.shutdown();
			AiRuntime.shutdown();
			LOGGER.info("✅ AI系统清理完成");
		});

		LOGGER.info("✨ Ausuka.ai Mod 加载完成！");
	}
}
