package com.keira.ai.tools;

import com.keira.ai.*;
import com.keira.tools.*;
import com.keira.tools.items.EnchantItemTool;
import com.keira.tools.items.GiveItemTool;
import com.keira.tools.items.ItemSearchTool;
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
    public final EnchantItemTool enchantItemTool;
    public final CommandExecutionTool commandExecutionTool;

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
        this.enchantItemTool = new EnchantItemTool(server);

        AiRuntime.initModAdminSystem(server);
        this.adminTools = new AdminTools(server, AiRuntime.getModAdminSystem());
        this.commandExecutionTool = new CommandExecutionTool(server);
    }
}
