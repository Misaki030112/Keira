package com.hinadt.command.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAiChatSessionStore implements AiChatSessionStore {
    private final Set<String> players = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isInChat(String playerName) {
        return players.contains(playerName);
    }

    @Override
    public void enter(String playerName) {
        players.add(playerName);
    }

    @Override
    public void exit(String playerName) {
        players.remove(playerName);
    }

    @Override
    public int count() {
        return players.size();
    }
}

