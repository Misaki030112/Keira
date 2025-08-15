package com.hinadt.ai.context;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;

public class PlayerContextBuilder {

    public String build(ServerPlayerEntity player) {
        StringBuilder context = new StringBuilder();

        // 基本信息
        String name = player.getName().getString();
        context.append("**玩家基本信息**:\n");
        context.append(String.format("- 名称: %s\n", name));
        context.append(String.format("- 生命值: %.0f/20\n", player.getHealth()));
        context.append(String.format("- 饥饿值: %d/20\n", player.getHungerManager().getFoodLevel()));
        context.append(String.format("- 所在世界: %s\n", getWorldDisplayName(player.getWorld())));

        // 位置
        BlockPos pos = player.getBlockPos();
        context.append("\n**位置信息**:\n");
        context.append(String.format("- 坐标: (%d, %d, %d)\n", pos.getX(), pos.getY(), pos.getZ()));

        // 生物群系
        try {
            var biomeEntry = player.getWorld().getBiome(pos);
            var biomeKey = biomeEntry.getKey();
            if (biomeKey.isPresent()) {
                String biomeName = biomeKey.get().getValue().getPath();
                context.append(String.format("- 当前生物群系: %s\n", biomeName));
                context.append(getBiomeCharacteristics(biomeName));
            }
        } catch (Exception ignored) {}

        // 时间和天气
        context.append("\n**环境状态**:\n");
        long timeOfDay = player.getWorld().getTimeOfDay() % 24000;
        context.append(String.format("- 游戏时间: %s (%d游戏刻)\n", getTimeDescription(timeOfDay), timeOfDay));
        context.append(String.format("- 天气: %s\n", getWeatherDescription(player.getWorld())));

        // 安全性
        context.append("\n**周围环境**:\n");
        if (player.getWorld().getRegistryKey() == World.NETHER) {
            context.append("- ⚠️ 下界环境：危险，注意岩浆和敌对生物\n");
        } else if (player.getWorld().getRegistryKey() == World.END) {
            context.append("- ⚠️ 末地环境：极度危险，注意末影龙和虚空\n");
        } else {
            boolean isDangerous = timeOfDay > 13000 && timeOfDay < 23000;
            context.append(isDangerous ? "- ⚠️ 夜晚时间：怪物活跃，建议寻找安全场所\n" : "- ✅ 白天时间：相对安全，适合探索和建造\n");
        }

        return context.toString();
    }

    private String getBiomeCharacteristics(String biomeName) {
        return switch (biomeName.toLowerCase()) {
            case "plains", "sunflower_plains" -> "- 特征: 平坦开阔，适合建造大型建筑，村庄常见\n";
            case "forest", "birch_forest", "dark_forest" -> "- 特征: 木材丰富，适合建造木屋，注意夜晚的敌对生物\n";
            case "desert" -> "- 特征: 干燥炎热，沙石丰富，有沙漠神殿，注意缺水\n";
            case "mountains", "mountain_meadow" -> "- 特征: 地势险峻，矿物丰富，视野开阔，适合建造山顶建筑\n";
            case "ocean", "deep_ocean" -> "- 特征: 水下环境，海洋生物丰富，有海洋遗迹，需要水下呼吸\n";
            case "swamp" -> "- 特征: 湿地环境，史莱姆活跃，有女巫小屋，移动缓慢\n";
            case "taiga", "snowy_taiga" -> "- 特征: 寒冷针叶林，狼群出没，木材和雪丰富\n";
            case "jungle" -> "- 特征: 茂密丛林，移动困难，豹猫出没，有丛林神殿\n";
            case "savanna" -> "- 特征: 热带草原，金合欢木特色，适合畜牧业\n";
            case "badlands", "mesa" -> "- 特征: 荒地地形，陶瓦资源丰富，金矿较多\n";
            case "mushroom_fields" -> "- 特征: 罕见蘑菇岛，无敌对生物，蘑菇牛栖息地\n";
            default -> "- 特征: 独特的生物群系，有其特殊的资源和特点\n";
        };
    }

    private String getTimeDescription(long timeOfDay) {
        if (timeOfDay < 1000) return "凌晨";
        if (timeOfDay < 6000) return "上午";
        if (timeOfDay < 12000) return "下午";
        if (timeOfDay < 18000) return "傍晚";
        return "夜晚";
    }

    private String getWeatherDescription(ServerWorld world) {
        if (world.isThundering()) return "雷暴⛈️";
        if (world.isRaining()) return "下雨🌧️";
        return "晴朗☀️";
    }

    private String getWorldDisplayName(ServerWorld world) {
        if (world.getRegistryKey() == World.OVERWORLD) return "主世界";
        if (world.getRegistryKey() == World.NETHER) return "下界";
        if (world.getRegistryKey() == World.END) return "末地";
        return world.getRegistryKey().getValue().toString();
    }
}
