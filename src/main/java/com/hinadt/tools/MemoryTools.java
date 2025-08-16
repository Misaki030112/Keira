package com.hinadt.tools;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.observability.RequestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.concurrent.CountDownLatch;
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
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        if (player == null) {
            return "❌ Player not found: " + playerName;
        }
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        
        server.execute(() -> {
            try {
                BlockPos pos = player.getBlockPos();
                String worldName = player.getWorld().getRegistryKey().getValue().toString();
                
                // Auto-generate a description if none is provided
                String finalDescription = description != null && !description.trim().isEmpty()
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
                AusukaAiMod.LOGGER.debug("{} [tool:save_location] saved name='{}' world='{}' pos=({},{},{})",
                        RequestContext.midTag(), locationName, worldName, pos.getX(), pos.getY(), pos.getZ());
                
            } catch (Exception e) {
                AusukaAiMod.LOGGER.error("Failed to save location memory", e);
                result.set("❌ Error while saving location memory: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "❌ Save location memory operation was interrupted";
        }
        
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
            AusukaAiMod.LOGGER.debug("{} [tool:get_saved_location] hit name='{}' world='{}'", RequestContext.midTag(), location.locationName(), location.world());
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
            
            AusukaAiMod.LOGGER.debug("{} [tool:list_saved_locations] return size={}",
                    RequestContext.midTag(), locations.size());
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
        try {
            AusukaAiMod.LOGGER.debug("{} [tool:delete_saved_location] params player='{}' name='{}'",
                    RequestContext.midTag(), playerName, locationName);
            boolean deleted = AiRuntime.getConversationMemory().deleteLocation(playerName, locationName);
            
            if (deleted) {
                AusukaAiMod.LOGGER.debug("{} [tool:delete_saved_location] deleted name='{}'",
                        RequestContext.midTag(), locationName);
                return String.format("✅ Deleted location memory: %s", locationName);
            } else {
                return String.format("❌ No location memory found to delete: %s", locationName);
            }
            
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Failed to delete location memory", e);
            return "❌ Error deleting location memory: " + e.getMessage();
        }
    }
}
