package com.hinadt.command.core;

import com.hinadt.persistence.MyBatisSupport;
import com.hinadt.persistence.mapper.ChatSessionMapper;

public class DatabaseAiChatSessionStore implements AiChatSessionStore {

    @Override
    public boolean isInChat(String playerName) {
        try (var session = MyBatisSupport.getFactory().openSession()) {
            ChatSessionMapper mapper = session.getMapper(ChatSessionMapper.class);
            Boolean v = mapper.isInChat(playerName);
            return v != null && v;
        }
    }

    @Override
    public void enter(String playerName) {
        try (var session = MyBatisSupport.getFactory().openSession(true)) {
            ChatSessionMapper mapper = session.getMapper(ChatSessionMapper.class);
            mapper.enter(playerName);
        }
    }

    @Override
    public void exit(String playerName) {
        try (var session = MyBatisSupport.getFactory().openSession(true)) {
            ChatSessionMapper mapper = session.getMapper(ChatSessionMapper.class);
            mapper.exit(playerName);
        }
    }

    @Override
    public int count() {
        try (var session = MyBatisSupport.getFactory().openSession()) {
            ChatSessionMapper mapper = session.getMapper(ChatSessionMapper.class);
            return mapper.countInChat();
        }
    }

    public static int cleanupOldEntriesHours(int hours) {
        try (var session = MyBatisSupport.getFactory().openSession(true)) {
            ChatSessionMapper mapper = session.getMapper(ChatSessionMapper.class);
            return mapper.deleteOlderThan(hours);
        }
    }
}
