package com.hinadt.ai.tools;

import com.hinadt.ai.*;
import com.hinadt.tools.*;
import com.hinadt.tools.items.GiveItemTool;
import com.hinadt.tools.items.ItemSearchTool;
import net.minecraft.server.MinecraftServer;

public class ToolRegistry {
    public final ItemSearchTool itemSearchTool;
    public final GiveItemTool giveItemTool;
    public final TeleportationTools teleportTools;
    public final WeatherTools weatherTools;
    public final PlayerStatsTools playerStatsTools;
    public final WorldAnalysisTools worldAnalysisTools;
    public final MemoryTools memoryTools;
    public final StatusEffectTools statusEffectTools;
    public final AdminTools adminTools;

    public ToolRegistry(MinecraftServer server) {
        // Item tools
        this.itemSearchTool = new ItemSearchTool();
        this.giveItemTool = new GiveItemTool(server);
        this.teleportTools = new TeleportationTools(server);
        this.weatherTools = new WeatherTools(server);
        this.playerStatsTools = new PlayerStatsTools(server);
        this.worldAnalysisTools = new WorldAnalysisTools(server);
        this.memoryTools = new MemoryTools(server);
        this.statusEffectTools = new StatusEffectTools(server);

        AiRuntime.initModAdminSystem(server);
        this.adminTools = new AdminTools(server, AiRuntime.getModAdminSystem());
    }
}
