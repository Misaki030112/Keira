package com.hinadt;

import net.fabricmc.api.ClientModInitializer;

public class AusukaAiModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
        AusukaAiMod.LOGGER.info("Ausuka.ai Mod 客户端初始化完成！");
    }
}
