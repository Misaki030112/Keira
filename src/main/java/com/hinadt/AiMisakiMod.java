package com.hinadt;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiMisakiMod implements ModInitializer {
	public static final String MOD_ID = "ai-misaki-mod";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("🤖 AI Misaki Mod 正在加载中...");

		// Initialize AI when server starts
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("🚀 服务器启动，正在初始化AI驱动系统...");
			try {
				// 初始化AI运行时
				AiRuntime.init();
				LOGGER.info("✅ AI运行时初始化完成");
				
				// 初始化AI聊天命令系统
				AiChatSystem.initialize(server);
				LOGGER.info("✅ AI聊天系统初始化完成");
				
				// 初始化智能自动消息系统
				IntelligentAutoMessageSystem.initialize(server);
				LOGGER.info("✅ 智能自动消息系统初始化完成");
				
				LOGGER.info("🎉 AI Misaki Mod 系统初始化完成！");
				
				// 发送启动欢迎消息
				server.execute(() -> {
					server.getPlayerManager().broadcast(
						net.minecraft.text.Text.of("§b🤖 [AI Misaki] §a系统上线！输入 §f/ai help §a查看功能"), 
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
			LOGGER.info("✅ AI系统清理完成");
		});

		LOGGER.info("✨ AI Misaki Mod 加载完成！");
	}
}
