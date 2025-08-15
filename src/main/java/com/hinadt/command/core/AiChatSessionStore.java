package com.hinadt.command.core;

/**
 * Abstraction for tracking which players are in AI chat mode.
 */
public interface AiChatSessionStore {
    boolean isInChat(String playerName);
    void enter(String playerName);
    void exit(String playerName);
    int count();
}

