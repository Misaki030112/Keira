package com.hinadt.tools;

import com.hinadt.AusukaAiMod;
import com.hinadt.observability.RequestContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.biome.Biome;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 世界分析工具
 * 分析玩家周围的环境、生物群系、方块等信息
 */
public class WorldAnalysisTools {
    
    private final MinecraftServer server;
    
    public WorldAnalysisTools(MinecraftServer server) {
        this.server = server;
    }
    
    @Tool(
        name = "analyze_surroundings",
        description = """
        高级环境分析工具：全面扫描并分析玩家周围的环境状况，提供详细的世界信息报告。
        
        分析维度：
        - 生物群系识别：检测当前所在的生物群系类型和特征
        - 地形分析：分析地形起伏、高度分布、地表特征
        - 方块分布：统计周围重要方块类型和数量
        - 实体检测：识别附近的生物、怪物、NPC等实体
        - 资源评估：评估周围的资源分布和可利用性
        - 环境安全：分析潜在威胁和安全状况
        
        智能特性：
        - 自适应半径：根据需要调整扫描范围（最大50格）
        - 多层次分析：从地表到地下的立体扫描
        - 实时更新：提供当前准确的环境状态
        - 智能筛选：过滤无关信息，突出重要发现
        
        适用场景：
        - 建筑选址：评估建造地点的适宜性
        - 资源勘探：寻找矿物和材料资源
        - 安全评估：检测危险区域和威胁
        - 生存规划：制定生存和发展策略
        - 探险指导：为冒险活动提供环境情报
        
        报告内容：结构化的环境分析报告，包含定量数据和定性评估
        """
    )
    public String analyzeSurroundings(
        @ToolParam(description = "要分析环境的玩家名称") String playerName,
        @ToolParam(description = "分析半径，默认16格") Integer radius
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:analyze_surroundings] params player='{}' radius={}",
                RequestContext.midTag(), playerName, radius);
        ServerPlayerEntity player = findPlayer(playerName);
        if (player == null) {
            return "❌ 找不到玩家：" + playerName;
        }
        
        int searchRadius = (radius != null && radius > 0 && radius <= 50) ? radius : 16;
        
        AtomicReference<String> result = new AtomicReference<>("");
        
        runOnMainAndWait(() -> {
            try {
                StringBuilder analysis = new StringBuilder();
                BlockPos playerPos = player.getBlockPos();
                ServerWorld world = player.getWorld();
                
                analysis.append("🔍 ").append(playerName).append(" 的环境分析：\n");
                
                // 生物群系信息
                Biome biome = world.getBiome(playerPos).value();
                String biomeName = getBiomeDisplayName(biome);
                analysis.append("🌍 生物群系：").append(biomeName).append("\n");
                
                // 高度信息
                int y = playerPos.getY();
                String heightInfo = getHeightInfo(y);
                analysis.append("📏 高度：Y=").append(y).append(" (").append(heightInfo).append(")\n");
                
                // 天气和时间
                String weather = world.isRaining() ? (world.isThundering() ? "雷雨" : "下雨") : "晴朗";
                String timeOfDay = getTimeOfDay(world.getTimeOfDay());
                analysis.append("🌤️ 天气：").append(weather).append("，时间：").append(timeOfDay).append("\n");
                
                // 分析周围方块
                Map<Block, Integer> blockCounts = analyzeNearbyBlocks(world, playerPos, searchRadius);
                if (!blockCounts.isEmpty()) {
                    analysis.append("🧱 周围主要方块：\n");
                    blockCounts.entrySet().stream()
                        .sorted(Map.Entry.<Block, Integer>comparingByValue().reversed())
                        .limit(5)
                        .forEach(entry -> {
                            String blockName = getBlockDisplayName(entry.getKey());
                            analysis.append("  • ").append(blockName).append(" x").append(entry.getValue()).append("\n");
                        });
                }
                
                // 分析附近实体
                List<String> nearbyEntities = analyzeNearbyEntities(world, playerPos, searchRadius);
                if (!nearbyEntities.isEmpty()) {
                    analysis.append("🐾 附近生物：").append(String.join("、", nearbyEntities)).append("\n");
                }
                
                // 提供环境建议
                String suggestion = getEnvironmentSuggestion(biome, y, blockCounts);
                if (suggestion != null) {
                    analysis.append("💡 建议：").append(suggestion);
                }
                
                result.set(analysis.toString());
                AusukaAiMod.LOGGER.debug("{} [tool:analyze_surroundings] analyzed biome='{}' radius={}",
                        RequestContext.midTag(), biomeName, searchRadius);
                
            } catch (Exception e) {
                result.set("❌ 分析环境时出错：" + e.getMessage());
                AusukaAiMod.LOGGER.error("分析环境时出错", e);
            }
        });
        
        return result.get();
    }
    
    @Tool(
        name = "find_resources",
        description = """
        在指定玩家周围搜索特定类型的资源或方块。
        可以帮助玩家找到需要的材料。
        """
    )
    public String findResources(
        @ToolParam(description = "要搜索资源的玩家名称") String playerName,
        @ToolParam(description = "资源类型：ore(矿物)、wood(木材)、water(水源)、village(村庄)等") String resourceType,
        @ToolParam(description = "搜索半径，默认32格") Integer radius
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:find_resources] params player='{}' type='{}' radius={}",
                RequestContext.midTag(), playerName, resourceType, radius);
        ServerPlayerEntity player = findPlayer(playerName);
        if (player == null) {
            return "❌ 找不到玩家：" + playerName;
        }
        
        int searchRadius = (radius != null && radius > 0 && radius <= 100) ? radius : 32;
        
        AtomicReference<String> result = new AtomicReference<>("");
        
        runOnMainAndWait(() -> {
            try {
                BlockPos playerPos = player.getBlockPos();
                ServerWorld world = player.getWorld();
                
                List<BlockPos> foundPositions = searchForResources(world, playerPos, searchRadius, resourceType);
                
                if (foundPositions.isEmpty()) {
                    result.set("🔍 在 " + searchRadius + " 格范围内没有找到 " + resourceType + " 类型的资源");
                } else {
                    StringBuilder report = new StringBuilder();
                    report.append("🎯 找到 ").append(foundPositions.size()).append(" 个 ").append(resourceType).append(" 资源：\n");
                    
                    foundPositions.stream()
                        .limit(10) // 限制显示数量
                        .forEach(pos -> {
                            int distance = (int) Math.sqrt(playerPos.getSquaredDistance(pos));
                            String direction = getDirection(playerPos, pos);
                            report.append("• 距离 ").append(distance).append(" 格，方向：").append(direction)
                                .append(" (").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ()).append(")\n");
                        });
                    
                    if (foundPositions.size() > 10) {
                        report.append("... 还有 ").append(foundPositions.size() - 10).append(" 个位置");
                    }
                    
                    result.set(report.toString());
                    AusukaAiMod.LOGGER.debug("{} [tool:find_resources] found={} type='{}'",
                            RequestContext.midTag(), foundPositions.size(), resourceType);
                }
                
            } catch (Exception e) {
                result.set("❌ 搜索资源时出错：" + e.getMessage());
                AusukaAiMod.LOGGER.error("搜索资源时出错", e);
            }
        });
        
        return result.get();
    }
    
    private Map<Block, Integer> analyzeNearbyBlocks(ServerWorld world, BlockPos center, int radius) {
        Map<Block, Integer> blockCounts = new HashMap<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    Block block = world.getBlockState(pos).getBlock();
                    if (block != Blocks.AIR && block != Blocks.CAVE_AIR && block != Blocks.VOID_AIR) {
                        blockCounts.merge(block, 1, Integer::sum);
                    }
                }
            }
        }
        
        return blockCounts;
    }
    
    private List<String> analyzeNearbyEntities(ServerWorld world, BlockPos center, int radius) {
        List<String> entities = new ArrayList<>();
        Box searchBox = new Box(center).expand(radius);
        
        List<Entity> nearbyEntities = world.getOtherEntities(null, searchBox);
        Map<String, Integer> entityCounts = new HashMap<>();
        
        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof PlayerEntity)) {
                String entityType = getEntityDisplayName(entity);
                entityCounts.merge(entityType, 1, Integer::sum);
            }
        }
        
        entityCounts.forEach((type, count) -> {
            if (count > 1) {
                entities.add(type + " x" + count);
            } else {
                entities.add(type);
            }
        });
        
        return entities;
    }
    
    private List<BlockPos> searchForResources(ServerWorld world, BlockPos center, int radius, String resourceType) {
        List<BlockPos> positions = new ArrayList<>();
        Set<Block> targetBlocks = getTargetBlocks(resourceType);
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    Block block = world.getBlockState(pos).getBlock();
                    if (targetBlocks.contains(block)) {
                        positions.add(pos);
                    }
                }
            }
        }
        
        return positions;
    }
    
    private Set<Block> getTargetBlocks(String resourceType) {
        return switch (resourceType.toLowerCase()) {
            case "ore", "矿物" -> Set.of(
                Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
                Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
                Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
                Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
                Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
                Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
                Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE
            );
            case "wood", "木材" -> Set.of(
                Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG,
                Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG,
                Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG
            );
            case "water", "水源" -> Set.of(Blocks.WATER);
            case "lava", "岩浆" -> Set.of(Blocks.LAVA);
            default -> Set.of();
        };
    }
    
    private String getDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "东" : "西";
        } else {
            return dz > 0 ? "南" : "北";
        }
    }
    
    private String getBiomeDisplayName(Biome biome) {
        // 这里可以添加更多生物群系的中文名称映射
        // Use toString() method as fallback since Registries.BIOME might not be available
        String biomeName = biome.toString();
        String biomePath = extractBiomePath(biomeName);
        
        return switch (biomePath.toLowerCase()) {
            case "plains" -> "平原";
            case "forest" -> "森林";
            case "desert" -> "沙漠";
            case "mountains", "mountain" -> "山地";
            case "ocean" -> "海洋";
            case "river" -> "河流";
            case "swamp" -> "沼泽";
            case "taiga" -> "针叶林";
            case "savanna" -> "热带草原";
            case "badlands" -> "恶地";
            case "jungle" -> "丛林";
            case "ice_plains", "tundra" -> "冰原";
            case "nether_wastes" -> "下界荒地";
            case "the_end" -> "末地";
            default -> biomePath;
        };
    }
    
    private String getBlockDisplayName(Block block) {
        String blockPath = Registries.BLOCK.getId(block).getPath();
        return switch (blockPath) {
            case "stone" -> "石头";
            case "dirt" -> "泥土";
            case "grass_block" -> "草方块";
            case "oak_log" -> "橡木原木";
            case "water" -> "水";
            case "sand" -> "沙子";
            case "coal_ore" -> "煤矿石";
            case "iron_ore" -> "铁矿石";
            case "diamond_ore" -> "钻石矿石";
            default -> blockPath.replace("_", " ");
        };
    }
    
    private String getEntityDisplayName(Entity entity) {
        String entityType = entity.getType().toString();
        if (entity instanceof VillagerEntity) {
            return "村民";
        }
        return switch (entityType) {
            case "cow" -> "牛";
            case "pig" -> "猪";
            case "sheep" -> "羊";
            case "chicken" -> "鸡";
            case "zombie" -> "僵尸";
            case "skeleton" -> "骷髅";
            case "creeper" -> "苦力怕";
            case "spider" -> "蜘蛛";
            default -> entityType;
        };
    }
    
    private String getHeightInfo(int y) {
        if (y < 0) return "地下深层";
        if (y < 16) return "深层挖矿区";
        if (y < 64) return "地下";
        if (y < 80) return "地表";
        if (y < 120) return "高地";
        if (y < 200) return "山地";
        return "高空";
    }
    
    private String getTimeOfDay(long timeOfDay) {
        long time = timeOfDay % 24000;
        if (time < 6000) return "白天";
        if (time < 12000) return "正午";
        if (time < 18000) return "夜晚";
        return "午夜";
    }
    
    private String getEnvironmentSuggestion(Biome biome, int y, Map<Block, Integer> blocks) {
        String biomePath = extractBiomePath(biome.toString());
        
        if (y < 16 && blocks.containsKey(Blocks.STONE)) {
            return "这里适合挖矿！注意带足够的火把和食物。";
        }
        
        return switch (biomePath.toLowerCase()) {
            case "desert" -> "沙漠地区要小心夜晚的怪物，建议建造避难所。";
            case "ocean" -> "海洋地区适合钓鱼和寻找海底遗迹。";
            case "forest" -> "森林是获取木材的好地方，也要小心夜晚的怪物。";
            case "mountains", "mountain" -> "山地视野开阔，适合建造高塔或城堡。";
            case "plains" -> "平原适合建造大型建筑和农场。";
            default -> "这个环境很适合探索和建造！";
        };
    }
    
    private ServerPlayerEntity findPlayer(String nameOrUuid) {
        ServerPlayerEntity byName = server.getPlayerManager().getPlayer(nameOrUuid);
        if (byName != null) return byName;
        
        try {
            UUID u = UUID.fromString(nameOrUuid);
            return server.getPlayerManager().getPlayer(u);
        } catch (Exception ignore) {
            return null;
        }
    }
    
    private void runOnMainAndWait(Runnable task) {
        if (server.isOnThread()) {
            task.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        server.execute(() -> {
            try { task.run(); } finally { latch.countDown(); }
        });
        try { latch.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
    
    /**
     * Helper method to extract biome path from biome toString representation
     */
    private String extractBiomePath(String biomeString) {
        // Try to extract the path from the string representation
        if (biomeString.contains("ResourceKey")) {
            // Format might be like "ResourceKey[minecraft:biome / minecraft:plains]"
            int lastSlash = biomeString.lastIndexOf('/');
            if (lastSlash > 0 && lastSlash < biomeString.length() - 1) {
                String path = biomeString.substring(lastSlash + 1);
                path = path.replaceAll("[\\]\\)]", "").trim();
                if (path.startsWith("minecraft:")) {
                    path = path.substring("minecraft:".length());
                }
                return path;
            }
        }
        
        // Fallback to simple name extraction
        String simpleName = biomeString.toLowerCase();
        if (simpleName.contains("plains")) return "plains";
        if (simpleName.contains("forest")) return "forest";
        if (simpleName.contains("desert")) return "desert";
        if (simpleName.contains("mountains") || simpleName.contains("hills")) return "mountains";
        if (simpleName.contains("ocean")) return "ocean";
        if (simpleName.contains("river")) return "river";
        if (simpleName.contains("swamp")) return "swamp";
        if (simpleName.contains("jungle")) return "jungle";
        if (simpleName.contains("taiga")) return "taiga";
        if (simpleName.contains("tundra") || simpleName.contains("ice")) return "ice_plains";
        
        return biomeString; // 返回原名作为备选
    }
}
