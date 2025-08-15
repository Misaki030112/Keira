package com.hinadt.command.core;

import com.hinadt.ai.AiWorkflowManager;
import net.minecraft.server.MinecraftServer;

/**
 * Lightweight service locator for command handlers.
 */
public final class AiServices {
    private static MinecraftServer server;
    private static AiWorkflowManager workflowManager;
    private static AiChatSessionStore sessionStore;

    private AiServices() {}

    public static void initialize(MinecraftServer srv) {
        server = srv;
        if (workflowManager == null) {
            workflowManager = new AiWorkflowManager(server);
        }
        if (sessionStore == null) {
            sessionStore = new DatabaseAiChatSessionStore();
        }
    }

    public static MinecraftServer server() { return server; }
    public static AiWorkflowManager workflow() { return workflowManager; }
    public static AiChatSessionStore sessions() { return sessionStore; }
}
