package com.hinadt.tools;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.observability.RequestContext;
import com.hinadt.util.MainThread;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.concurrent.atomic.AtomicReference;

/**
 * AI memory tools
 * - Location memory save/retrieve
 * - Player preference storage (future)
 * - Global server memory (future)
 */
public class MemoryTools {
    
    private final MinecraftServer server;
    
    public MemoryTools(MinecraftServer server) {
        this.server = server;
    }
    
    @Tool(
        name = "save_location",
        description = """
        Save a player-defined important location into the memory system. Use when the player says things like "remember this is my home".

        Details:
        - Save the player's current position with a meaningful name
        - Overwrites if the name already exists
        - Records world, coordinates, and a description
        - Enables later teleportation and recall

        Parameters:
        - playerName: player name
        - locationName: location name (e.g., "home", "farm", "mine")
        - description: optional description for better recognition
        """
    )
    public String saveLocation(
        @ToolParam(description = "Player name for the location memory") String playerName,
        @ToolParam(description = "Location name, such as 'home', 'farm', 'mine'") String locationName,
        @ToolParam(description = "Optional detailed description for the location") String description
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:save_location] params player='{}' name='{}' desc='{}'",
                RequestContext.midTag(), playerName, locationName, description);
        long startNanos = System.nanoTime();
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        if (player == null) {
            return "❌ Player not found: " + playerName;
        }
        final AtomicReference<String> result = new AtomicReference<>();
        MainThread.runSync(server, () -> {
            try {
                BlockPos pos = player.getBlockPos();
                String worldName = player.getWorld().getRegistryKey().getValue().toString();

                String finalDescription = (description != null && !description.trim().isEmpty())
                        ? description
                        : "Saved position in world " + worldName;

                AiRuntime.getConversationMemory().saveLocation(
                        playerName,
                        locationName,
                        worldName,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        finalDescription
                );

                result.set(String.format("✅ Saved location memory: %s → %s (%d, %d, %d) in %s",
                        locationName, finalDescription, pos.getX(), pos.getY(), pos.getZ(), worldName));
                AusukaAiMod.LOGGER.info("{} [tool:save_location] done name='{}' world='{}' pos=({},{},{}) cost={}ms",
                        RequestContext.midTag(), locationName, worldName, pos.getX(), pos.getY(), pos.getZ(),
                        (System.nanoTime() - startNanos)/1_000_000L);

            } catch (Exception e) {
                AusukaAiMod.LOGGER.error("Failed to save location memory", e);
                result.set("❌ Error while saving location memory: " + e.getMessage());
            }
        });
        return result.get();
    }
    
    @Tool(
        name = "get_saved_location",
        description = """
        Get a player's saved location by name. Supports exact and fuzzy matching.

        Details:
        - Try exact match first
        - Fallback to fuzzy search if needed
        - Returns full info (coords, world, description)
        """
    )
    public String getSavedLocation(
        @ToolParam(description = "Player name") String playerName,
        @ToolParam(description = "Location name or keyword") String locationName
    ) {
        long startNanos = System.nanoTime();
        try {
            AusukaAiMod.LOGGER.debug("{} [tool:get_saved_location] params player='{}' name='{}'",
                    RequestContext.midTag(), playerName, locationName);
            com.hinadt.persistence.record.LocationRecord location = 
                AiRuntime.getConversationMemory().getLocationForTeleport(playerName, locationName);
            
            if (location == null) {
                return String.format("❌ No location memory found for player %s: %s", playerName, locationName);
            }
            
            String out = String.format("📍 Location Info:\n" +
                "Name: %s\n" +
                "World: %s\n" +
                "Coords: (%.1f, %.1f, %.1f)\n" +
                "Description: %s",
                    location.locationName(), location.world(), location.x(), location.y(), location.z(), location.description());
            AusukaAiMod.LOGGER.info("{} [tool:get_saved_location] hit name='{}' world='{}' cost={}ms",
                    RequestContext.midTag(), location.locationName(), location.world(), (System.nanoTime()-startNanos)/1_000_000L);
            return out;
                
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Failed to get location memory", e);
            return "❌ Error fetching location memory: " + e.getMessage();
        }
    }
    
    @Tool(
        name = "list_saved_locations",
        description = """
        List all saved locations for a player.

        Details:
        - Shows all saved locations
        - Includes name, world, coordinates, and description
        """
    )
    public String listSavedLocations(
        @ToolParam(description = "Player name to list locations for") String playerName
    ) {
        long startNanos = System.nanoTime();
        try {
            AusukaAiMod.LOGGER.debug("{} [tool:list_saved_locations] params player='{}'",
                    RequestContext.midTag(), playerName);
            java.util.List<com.hinadt.persistence.record.LocationRecord> locations = 
                AiRuntime.getConversationMemory().getAllLocations(playerName);
            
            if (locations.isEmpty()) {
                return String.format("📍 Player %s has no saved locations yet.\n" +
                        "Use phrases like 'remember this is my home' to save the current position.", playerName);
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("📍 All saved locations for %s:\n\n", playerName));
            
            for (int i = 0; i < locations.size(); i++) {
                var loc = locations.get(i);
                result.append(String.format("%d. **%s**\n", i + 1, loc.locationName()));
                result.append(String.format("   Coords: (%.1f, %.1f, %.1f)\n", loc.x(), loc.y(), loc.z()));
                result.append(String.format("   World: %s\n", loc.world()));
                result.append(String.format("   Description: %s\n\n", loc.description()));
            }
            
            AusukaAiMod.LOGGER.info("{} [tool:list_saved_locations] return size={} cost={}ms",
                    RequestContext.midTag(), locations.size(), (System.nanoTime()-startNanos)/1_000_000L);
            return result.toString();
            
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Failed to list location memory", e);
            return "❌ Error listing location memory: " + e.getMessage();
        }
    }
    
    @Tool(
        name = "delete_saved_location", 
        description = """
        Delete a player's saved location by name.

        Details:
        - Permanently deletes the specified location memory
        - Not recoverable after deletion
        """
    )
    public String deleteSavedLocation(
        @ToolParam(description = "Player name") String playerName,
        @ToolParam(description = "Location name to delete") String locationName
    ) {
        long startNanos = System.nanoTime();
        try {
            AusukaAiMod.LOGGER.debug("{} [tool:delete_saved_location] params player='{}' name='{}'",
                    RequestContext.midTag(), playerName, locationName);
            boolean deleted = AiRuntime.getConversationMemory().deleteLocation(playerName, locationName);
            
            if (deleted) {
                AusukaAiMod.LOGGER.info("{} [tool:delete_saved_location] deleted name='{}' cost={}ms",
                        RequestContext.midTag(), locationName, (System.nanoTime()-startNanos)/1_000_000L);
                return String.format("✅ Deleted location memory: %s", locationName);
            } else {
                return String.format("❌ No location memory found to delete: %s", locationName);
            }
            
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Failed to delete location memory", e);
            return "❌ Error deleting location memory: " + e.getMessage();
        }
    }
    
    @Tool(
        name = "find_location",
        description = """
        Find a saved location and return a structured JSON result for tool chaining.

        INPUT
          playerName (string): whose memory to search
          locationName (string): name or keyword

        OUTPUT (JSON)
          {
            "ok": true/false,
            "code": "OK" | "NOT_FOUND" | "ERROR",
            "message": "<short explanation>",
            "name": "<location name>",
            "world": "<dimension id>",
            "x": <number>,
            "y": <number>,
            "z": <number>,
            "description": "<text>"
          }
        """
    )
    public String findLocation(
        @ToolParam(description = "Player name to search") String playerName,
        @ToolParam(description = "Location name or keyword") String locationName
    ) {
        long startNanos = System.nanoTime();
        try {
            AusukaAiMod.LOGGER.debug("{} [tool:find_location] params player='{}' name='{}'",
                    RequestContext.midTag(), playerName, locationName);
            var loc = AiRuntime.getConversationMemory().getLocationForTeleport(playerName, locationName);
            if (loc == null) {
                JsonObject root = new JsonObject();
                root.addProperty("ok", false);
                root.addProperty("code", "NOT_FOUND");
                root.addProperty("message", "No location found");
                return root.toString();
            }
            AusukaAiMod.LOGGER.info("{} [tool:find_location] hit name='{}' world='{}' cost={}ms",
                    RequestContext.midTag(), loc.locationName(), loc.world(), (System.nanoTime()-startNanos)/1_000_000L);
            JsonObject root = new JsonObject();
            root.addProperty("ok", true);
            root.addProperty("code", "OK");
            root.addProperty("message","");
            root.addProperty("name", loc.locationName());
            root.addProperty("world", loc.world());
            root.addProperty("x", loc.x());
            root.addProperty("y", loc.y());
            root.addProperty("z", loc.z());
            root.addProperty("description", loc.description());
            return root.toString();
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Failed to find location", e);
            JsonObject root = new JsonObject();
            root.addProperty("ok", false);
            root.addProperty("code", "ERROR");
            root.addProperty("message", "Exception");
            return root.toString();
        }
    }
}
