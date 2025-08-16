package com.hinadt.ai;

import com.hinadt.AusukaAiMod;
import com.hinadt.persistence.MyBatisSupport;
import com.hinadt.persistence.mapper.ConversationMapper;
import com.hinadt.persistence.mapper.LocationMapper;
import com.hinadt.persistence.model.ConversationRow;
import net.minecraft.util.math.Vec3d;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conversation memory system backed by H2 via MyBatis.
 * - Persists conversation history for retrieval and long-term memory.
 * - The current active session ID is in-memory only (per online player).
 * Data files live under ./config/ausuka-ai/
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
        for (ConversationRecord record : history) {
            String role = switch (record.messageType) {
                case USER -> "User";
                case AI -> "AI";
                case SYSTEM -> "System";
            };
            context.append(String.format("[%s] %s: %s\n",
                    record.timestamp.toString().substring(11, 19), // show time part only
                    role,
                    record.content));
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
        updateCache(playerName, new ConversationRecord(sessionId, type, content, LocalDateTime.now(), "{}"));
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
            List<ConversationRow> rows = mapper.getRecent(playerName, sessionId, limit);
            for (ConversationRow r : rows) {
                MessageType type = MessageType.from(r.getMessageType());
                records.add(new ConversationRecord(
                        r.getSessionId(),
                        type,
                        r.getMessageContent(),
                        r.getTimestamp(),
                        r.getContextData()
                ));
            }
            Collections.reverse(records);
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Failed to load conversation records", e);
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
            AusukaAiMod.LOGGER.error("Failed to save conversation record", e);
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
            cache.removeFirst();
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

    /** Conversation record .*/
    public record ConversationRecord(String sessionId, MessageType messageType, String content, LocalDateTime timestamp,
                                         String contextData) {
    }

    /** Location data for location memory feature. */
    public record LocationData(String name, String world, double x, double y, double z, String description) {
        public Vec3d toVec3d() {
                return new Vec3d(x, y, z);
        }
    }
    
    // ==================== Location memory ====================
    
    /**
     * Save or update a player's named location.
     */
    public void saveLocation(String playerName, String locationName, String world, double x, double y, double z, String description) {
        try (var session = MyBatisSupport.getFactory().openSession(true)) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            mapper.upsert(playerName, locationName, world, x, y, z, description);
            AusukaAiMod.LOGGER.info("Location memory saved: player={} name={} world={} ({}, {}, {})", playerName, locationName, world, x, y, z);
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Failed to save location memory", e);
        }
    }
    
    /**
     * Get a location for teleport by exact or fuzzy name.
     */
    public LocationData getLocationForTeleport(String playerName, String destination) {
        // 首先尝试精确匹配
        LocationData exact = getExactLocation(playerName, destination);
        if (exact != null) {
            return exact;
        }
        
        // 然后尝试模糊匹配
        return getFuzzyLocation(playerName, destination);
    }
    
    /**
     * Exact location match.
     */
    private LocationData getExactLocation(String playerName, String locationName) {
        try (var session = MyBatisSupport.getFactory().openSession()) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            com.hinadt.persistence.model.LocationRow row = mapper.getExact(playerName, locationName);
            if (row != null) {
                return new LocationData(
                        row.getLocationName(),
                        row.getWorld(),
                        row.getX(),
                        row.getY(),
                        row.getZ(),
                        row.getDescription()
                );
            }
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Failed to load location memory", e);
        }
        
        return null;
    }
    
    /**
     * Fuzzy location match.
     */
    private LocationData getFuzzyLocation(String playerName, String searchTerm) {
        try (var session = MyBatisSupport.getFactory().openSession()) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            com.hinadt.persistence.model.LocationRow row = mapper.getFuzzy(playerName, "%" + searchTerm + "%");
            if (row != null) {
                return new LocationData(
                        row.getLocationName(),
                        row.getWorld(),
                        row.getX(),
                        row.getY(),
                        row.getZ(),
                        row.getDescription()
                );
            }
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Failed to fuzzy search location", e);
        }
        
        return null;
    }
    
    /**
     * List all saved locations for a player.
     */
    public List<LocationData> getAllLocations(String playerName) {
        List<LocationData> locations = new ArrayList<>();
        
        try (var session = MyBatisSupport.getFactory().openSession()) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            java.util.List<com.hinadt.persistence.model.LocationRow> rows = mapper.getAll(playerName);
            for (var r : rows) {
                locations.add(new LocationData(
                        r.getLocationName(),
                        r.getWorld(),
                        r.getX(),
                        r.getY(),
                        r.getZ(),
                        r.getDescription()
                ));
            }
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Failed to list all locations", e);
        }
        
        return locations;
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
            AusukaAiMod.LOGGER.error("Failed to delete location memory", e);
            return false;
        }
    }
}
