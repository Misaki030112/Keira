package com.hinadt;

import net.fabricmc.api.ClientModInitializer;

public class AiMisakiModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		AiMisakiMod.LOGGER.info("AI Misaki Mod 客户端初始化完成！");
	}
}
