package com.keira;

import net.fabricmc.api.ClientModInitializer;

public class KeiraAiModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Client-only setup if needed in future
        KeiraAiMod.LOGGER.info("Keira Mod client initialized.");
    }
}
