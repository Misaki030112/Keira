package com.hinadt.ai.tools;

import com.hinadt.ai.*;
import com.hinadt.tools.*;
import net.minecraft.server.MinecraftServer;

public class ToolRegistry {
    public final McTools mcTools;
    public final TeleportationTools teleportTools;
    public final WeatherTools weatherTools;
    public final PlayerStatsTools playerStatsTools;
    public final WorldAnalysisTools worldAnalysisTools;
    public final MemoryTools memoryTools;
    public final AdminTools adminTools;

    public ToolRegistry(MinecraftServer server) {
        this.mcTools = new McTools(server);
        this.teleportTools = new TeleportationTools(server);
        this.weatherTools = new WeatherTools(server);
        this.playerStatsTools = new PlayerStatsTools(server);
        this.worldAnalysisTools = new WorldAnalysisTools(server);
        this.memoryTools = new MemoryTools(server);

        AiRuntime.initModAdminSystem(server);
        this.adminTools = new AdminTools(server, AiRuntime.getModAdminSystem());
    }
}

