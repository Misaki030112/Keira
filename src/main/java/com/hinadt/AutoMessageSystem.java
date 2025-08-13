package com.hinadt;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 自动消息系统 - 定期发送有趣的消息、提示和建议
 */
public class AutoMessageSystem {
    
    private static MinecraftServer server;
    private static ScheduledExecutorService scheduler;
    private static final Random random = new Random();
    
    // 游戏提示消息
    private static final List<String> GAME_TIPS = List.of(
        "💡 小贴士：在下雨天钓鱼会更容易钓到鱼哦！",
        "💡 小贴士：使用附魔台时，在周围放置书架可以获得更好的附魔！",
        "💡 小贴士：村民的职业可以通过他们使用的工作方块来改变！",
        "💡 小贴士：在末地传送门附近建造基地要小心，末影人会偷走你的方块！",
        "💡 小贴士：铁傀儡可以保护村庄免受僵尸的袭击！",
        "💡 小贴士：使用TNT开采时记得保持安全距离！",
        "💡 小贴士：潜行状态下可以避免从方块边缘掉落！",
        "💡 小贴士：红石火把可以为红石电路提供信号！",
        "💡 小贴士：在沙漠中寻找沙漠神殿，里面有宝贵的战利品！",
        "💡 小贴士：使用胡萝卜钓竿可以控制猪的移动方向！"
    );
    
    // 励志消息
    private static final List<String> MOTIVATIONAL_MESSAGES = List.of(
        "🌟 永远不要放弃建造你的梦想城堡！",
        "⚡ 每一次挖掘都可能发现钻石！",
        "🚀 探索未知的世界，发现新的奇迹！",
        "🏆 今天又是充满可能性的一天！",
        "💪 相信自己，你可以建造任何想象中的建筑！",
        "🌈 每个创造者都是独特的艺术家！",
        "⭐ 勇敢地面对怪物，成为真正的英雄！",
        "🔥 坚持就是胜利，继续你的冒险之旅！",
        "🎯 设定目标，然后一步步实现它！",
        "🎨 让创造力自由飞翔，建造属于你的世界！"
    );
    
    // 有趣的事实
    private static final List<String> FUN_FACTS = List.of(
        "🤔 有趣的事实：史蒂夫的头是完美的立方体！",
        "🤔 有趣的事实：末影人害怕水，这是它们的弱点！",
        "🤔 有趣的事实：苦力怕最初是猪的编程错误造成的！",
        "🤔 有趣的事实：一天有20分钟，相当于现实中的24小时！",
        "🤔 有趣的事实：下界的岩浆海平面在Y=31！",
        "🤔 有趣的事实：村民会根据时间进行不同的活动！",
        "🤔 有趣的事实：蜘蛛在白天是友好的，除非被攻击！",
        "🤔 有趣的事实：钻石在Y=5-12层最容易找到！",
        "🤔 有趣的事实：末影龙的名字叫做Jean？",
        "🤔 有趣的事实：红石可以传输信号最远15格！"
    );
    
    public static void initialize(MinecraftServer minecraftServer) {
        server = minecraftServer;
        scheduler = Executors.newScheduledThreadPool(1);
        
        // 每5分钟发送一次自动消息
        scheduler.scheduleAtFixedRate(AutoMessageSystem::sendRandomMessage, 5, 5, TimeUnit.MINUTES);
        
        // 每30分钟发送一次游戏提示
        scheduler.scheduleAtFixedRate(AutoMessageSystem::sendGameTip, 10, 30, TimeUnit.MINUTES);
        
        AiMisakiMod.LOGGER.info("自动消息系统已启动！");
    }
    
    public static void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
    
    private static void sendRandomMessage() {
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            server.execute(() -> {
                List<String> allMessages = List.of(
                    getRandomMotivationalMessage(),
                    getRandomFunFact()
                );
                
                String message = allMessages.get(random.nextInt(allMessages.size()));
                server.getPlayerManager().broadcast(
                    Text.of("§d[AI Misaki] §f" + message), 
                    false
                );
            });
        }
    }
    
    private static void sendGameTip() {
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            server.execute(() -> {
                String tip = getRandomGameTip();
                server.getPlayerManager().broadcast(
                    Text.of("§a[AI Misaki] §f" + tip), 
                    false
                );
            });
        }
    }
    
    public static String getRandomGameTip() {
        return GAME_TIPS.get(random.nextInt(GAME_TIPS.size()));
    }
    
    public static String getRandomMotivationalMessage() {
        return MOTIVATIONAL_MESSAGES.get(random.nextInt(MOTIVATIONAL_MESSAGES.size()));
    }
    
    public static String getRandomFunFact() {
        return FUN_FACTS.get(random.nextInt(FUN_FACTS.size()));
    }
    
    // 根据玩家活动发送相关提示
    public static void sendContextualTip(ServerPlayerEntity player, String context) {
        server.execute(() -> {
            String tip = getContextualTip(context);
            if (tip != null) {
                player.sendMessage(Text.of("§e[AI Misaki] §f" + tip));
            }
        });
    }
    
    private static String getContextualTip(String context) {
        return switch (context.toLowerCase()) {
            case "mining" -> "⛏️ 挖矿时记得带足够的食物和火把！钻石通常在Y=5-12层！";
            case "building" -> "🏗️ 建造时可以先用便宜的材料做框架，再用好看的材料装饰！";
            case "farming" -> "🌱 农场需要充足的光照和水源，记得保护农作物不被践踏！";
            case "exploring" -> "🗺️ 探索时记得带指南针和地图，标记重要的位置！";
            case "nether" -> "🔥 在下界要小心岩浆和恶魂，建议穿戴防火装备！";
            case "end" -> "🌌 在末地要小心末影人，不要直视它们的眼睛！";
            default -> null;
        };
    }
}