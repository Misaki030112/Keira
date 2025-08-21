package com.keira.ai;

import com.keira.KeiraAiMod;
import com.keira.persistence.MyBatisSupport;
import com.keira.persistence.mapper.ConversationMapper;
import com.keira.persistence.mapper.LocationMapper;
import com.keira.persistence.record.ConversationRecord;
import com.keira.persistence.record.LocationRecord;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conversation memory system backed by H2 via MyBatis.
 * - Persists conversation history for retrieval and long-term memory.
 * - The current active session ID is in-memory only (per online player).
 * Data files live under ./config/keira-ai/
 */
public class ConversationMemorySystem {

    /**
     * Allowed message types for conversation records.
     */
    public enum MessageType {
        USER, AI, SYSTEM;

        public static MessageType from(String value) {
            if (value == null) throw new IllegalArgumentException("messageType cannot be null");
            try {
                return MessageType.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unsupported messageType: " + value);
            }
        }
    }

    // Current session id per online player (in-memory only)
    private final Map<String, String> playerSessions = new ConcurrentHashMap<>();
    // Recent conversation cache per player (in-memory, convenience only)
    private final Map<String, List<ConversationRecord>> conversationCache = new ConcurrentHashMap<>();

    public ConversationMemorySystem() {
        // Ensure MyBatis initialized and schema ready
        MyBatisSupport.init();
    }
    
    /**
     * Build conversation context (English) for AI prompting.
     */
    public String getConversationContext(String playerName) {
        List<ConversationRecord> history = getRecentConversation(playerName, 10);
        if (history.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("## Conversation History Context ##\n");
        for (ConversationRecord r : history) {
            String type = r.messageType() == null ? "SYSTEM" : r.messageType().toUpperCase();
            String role = switch (type) { case "USER" -> "User"; case "AI" -> "AI"; default -> "System"; };
            context.append(String.format("[%s] %s: %s\n",
                    r.timestamp().toString().substring(11, 19),
                    role,
                    r.messageContent()));
        }
        context.append("## Current Conversation ##\n");
        
        return context.toString();
    }
    
    /**
     * Save a user message for the current session.
     */
    public void saveUserMessage(String playerName, String message) {
        saveMessage(playerName, MessageType.USER, message);
    }
    
    /**
     * Save an AI response for the current session.
     */
    public void saveAiResponse(String playerName, String response) {
        saveMessage(playerName, MessageType.AI, response);
    }

    /**
     * Unified API to save a message for the current session and update cache.
     */
    public void saveMessage(String playerName, MessageType type, String content) {
        String sessionId = getCurrentSessionId(playerName);
        persistMessage(playerName, sessionId, type, content, "{}");
        updateCache(playerName, new ConversationRecord(sessionId, type.name(), content, LocalDateTime.now(), "{}"));
    }
    
    /**
     * Start a new conversation session. Stored only in memory.
     */
    public String startNewConversation(String playerName) {
        String newSessionId = UUID.randomUUID().toString().substring(0, 8);
        playerSessions.put(playerName, newSessionId);
        
        // Clear cache for the player
        conversationCache.remove(playerName);
        
        // Mark session start
        persistMessage(playerName, newSessionId, MessageType.SYSTEM, "New conversation session started", "{}");
        
        return newSessionId;
    }
    
    /**
     * Get the current session ID for a player. Does NOT read from history.
     * If absent (new player or after logout), a new session is created.
     */
    public String getCurrentSessionId(String playerName) {
        String cached = playerSessions.get(playerName);
        if (cached != null) return cached;
        // Create a fresh session instead of resuming from history
        return startNewConversation(playerName);
    }
    
    /**
     * Load recent conversation records for the active session from persistence.
     */
    private List<ConversationRecord> getRecentConversation(String playerName, int limit) {
        String sessionId = getCurrentSessionId(playerName);
        // Prefer in-memory cache for the active session
        List<ConversationRecord> cached = conversationCache.get(playerName);
        if (cached != null && !cached.isEmpty()) {
            int from = Math.max(0, cached.size() - limit);
            return new ArrayList<>(cached.subList(from, cached.size()));
        }

        List<ConversationRecord> records = new ArrayList<>();
        try (var session = MyBatisSupport.getFactory().openSession()) {
            ConversationMapper mapper = session.getMapper(ConversationMapper.class);
            List<ConversationRecord> rows = mapper.getRecent(playerName, sessionId, limit);
            records.addAll(rows);
            Collections.reverse(records);
        } catch (Exception e) {
            KeiraAiMod.LOGGER.error("Failed to load conversation records", e);
        }

        return records;
    }
    
    /**
     * Persist a conversation message.
     */
    private void persistMessage(String playerName, String sessionId, MessageType messageType, String content, String contextData) {
        try (var session = MyBatisSupport.getFactory().openSession(true)) {
            ConversationMapper mapper = session.getMapper(ConversationMapper.class);
            mapper.insertMessage(playerName, sessionId, messageType.name(), content, contextData);
        } catch (Exception e) {
            KeiraAiMod.LOGGER.error("Failed to save conversation record", e);
        }
    }
    
    /**
     * Update in-memory cache for quick context assembly.
     */
    private void updateCache(String playerName, ConversationRecord record) {
        conversationCache.computeIfAbsent(playerName, k -> new ArrayList<>()).add(record);
        
        // Keep cache size bounded
        List<ConversationRecord> cache = conversationCache.get(playerName);
        if (cache.size() > 50) {
            cache.remove(0);
        }
    }
    
    /**
     * No-op: connection pool managed elsewhere.
     */
    public void shutdown() { /* Managed by pool, nothing to do */ }

    /**
     * Clear in-memory session and cache for a player (on logout).
     */
    public void clearSession(String playerName) {
        playerSessions.remove(playerName);
        conversationCache.remove(playerName);
    }


    // ==================== Location memory ====================
    
    /**
     * Save or update a player's named location.
     */
    public void saveLocation(String playerName, String locationName, String world, double x, double y, double z, String description) {
        try (var session = MyBatisSupport.getFactory().openSession(true)) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            mapper.upsert(playerName, locationName, world, x, y, z, description);
            KeiraAiMod.LOGGER.info("Location memory saved: player={} name={} world={} ({}, {}, {})", playerName, locationName, world, x, y, z);
        } catch (Exception e) {
            KeiraAiMod.LOGGER.error("Failed to save location memory", e);
        }
    }
    
    /**
     * Get a location for teleport by exact or fuzzy name.
     */
    public LocationRecord getLocationForTeleport(String playerName, String destination) {
        LocationRecord exact = getExactLocation(playerName, destination);
        if (exact != null) {
            return exact;
        }
        
        return getFuzzyLocation(playerName, destination);
    }
    
    /**
     * Exact location match.
     */
    private LocationRecord getExactLocation(String playerName, String locationName) {
        try (var session = MyBatisSupport.getFactory().openSession()) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            LocationRecord row = mapper.getExact(playerName, locationName);
            if (row != null) return row;
        } catch (Exception e) {
            KeiraAiMod.LOGGER.error("Failed to load location memory", e);
        }
        
        return null;
    }
    
    /**
     * Fuzzy location match.
     */
    private LocationRecord getFuzzyLocation(String playerName, String searchTerm) {
        try (var session = MyBatisSupport.getFactory().openSession()) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            LocationRecord row = mapper.getFuzzy(playerName, "%" + searchTerm + "%");
            if (row != null) return row;
        } catch (Exception e) {
            KeiraAiMod.LOGGER.error("Failed to fuzzy search location", e);
        }
        
        return null;
    }
    
    /**
     * List all saved locations for a player.
     */
    public List<LocationRecord> getAllLocations(String playerName) {
        try (var session = MyBatisSupport.getFactory().openSession()) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            return mapper.getAll(playerName);
        } catch (Exception e) {
            KeiraAiMod.LOGGER.error("Failed to list all locations", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Delete a named location for a player.
     */
    public boolean deleteLocation(String playerName, String locationName) {
        try (var session = MyBatisSupport.getFactory().openSession(true)) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            int deleted = mapper.delete(playerName, locationName);
            return deleted > 0;
        } catch (Exception e) {
            KeiraAiMod.LOGGER.error("Failed to delete location memory", e);
            return false;
        }
    }
}
