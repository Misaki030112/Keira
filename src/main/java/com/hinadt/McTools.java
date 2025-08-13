package com.hinadt;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spring AI 工具集合
 * - 工具1：列出可用物品（支持过滤/限制条数），给 AI 作为“知识/选项”
 * - 工具2：给玩家发物品（主线程执行，背包满则在头顶掉落）
 */
public class McTools {

    private final MinecraftServer server;

    public McTools(MinecraftServer server) {
        this.server = server;
    }

    /* ===================== 工具 1：列出物品 ===================== */

    @Tool(
            name = "list_items",
            description = """
            列出 Minecraft 物品（用于让 AI 选择/联想）。返回数组，每个元素包含：
            { "id": "minecraft:diamond_sword", "name": "Diamond Sword", "maxStack": 1 }。
            可用参数：
            - query：按注册ID或名称包含匹配过滤（可选）
            - limit：最多返回多少条（默认50，范围1~200）
            """
    )
    public List<ItemInfo> listItems(
            @ToolParam(description = "按注册ID或可读名称的包含匹配过滤，例：'diamond'（可选）")
            String query,
            @ToolParam(description ="返回条数上限，默认50，范围1~200")
            Integer limit
    ) {
        final String q = (query == null) ? "" : query.toLowerCase(Locale.ROOT);
        int cap = (limit == null) ? 50 : Math.max(1, Math.min(200, limit));

        List<ItemInfo> out = new ArrayList<>(cap);
        for (Item item : Registries.ITEM) { // 遍历所有已注册物品
            if (out.size() >= cap) break;

            Identifier id = Registries.ITEM.getId(item);           // 如 minecraft:diamond_sword
            String name = new ItemStack(item).getName().getString(); // 可读名称（英文，服务端环境）
            int maxStack = new ItemStack(item).getMaxCount();

            if (!q.isEmpty()) {
                String rid = id.toString().toLowerCase(Locale.ROOT);
                String rn  = name.toLowerCase(Locale.ROOT);
                if (!rid.contains(q) && !rn.contains(q)) continue;
            }

            out.add(new ItemInfo(id.toString(), name, maxStack));
        }
        return out;
    }

    public static record ItemInfo(String id, String name, int maxStack) {}

    /* ===================== 工具 2：给玩家发物品 ===================== */

    @Tool(
            name = "give_item",
            description = """
            给指定玩家发放某件物品。如果背包放不下，剩余部分会在玩家头顶“掉落”。
            参数：
            - player：玩家名（推荐）或 UUID 字符串
            - itemId：物品注册ID（如 "minecraft:diamond_sword"）
            - count：数量（1~640），超过最大叠加会自动拆分多堆
            返回：形如 "ok: gave=3, dropped=0" 的结果说明
            """
    )
    public String giveItem(
            @ToolParam(description ="玩家名称或UUID（优先按名称找）") String player,
            @ToolParam(description ="物品注册ID，例如 'minecraft:diamond_sword'") String itemId,
            @ToolParam(description ="数量（1~640），会自动按最大叠加拆分") Integer count
    ) {
        int n = (count == null) ? 1 : Math.max(1, Math.min(640, count));

        // 解析物品 ID
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return "error: invalid itemId";

        // 取物品（存在性校验）
        Item item = Registries.ITEM.get(id);
        if (item == Items.AIR) return "error: item not found: " + itemId;

        // 找玩家（先按名称，找不到再按 UUID）
        ServerPlayerEntity target = findPlayer(player);
        if (target == null) return "error: player not online: " + player;

        // 在主线程执行发放/掉落并等待完成
        AtomicReference<String> result = new AtomicReference<>("error");
        runOnMainAndWait(() -> {
            int given = 0, dropped = 0;
            int maxPerStack = new ItemStack(item).getMaxCount(); // 通常 64 或 1

            int remaining = n;
            while (remaining > 0) {
                int take = Math.min(maxPerStack, remaining);
                ItemStack stack = new ItemStack(item, take);

                boolean ok = target.giveItemStack(stack.copy());
                if (ok) {
                    given += take;
                } else {
                    // 背包放不下：从空中掉落
                    var world = target.getServerWorld();
                    var drop = target.dropItem(stack, false);
                    if (drop != null) {
                        drop.setPosition(target.getX(), target.getY() + 5.0, target.getZ()); // 天上掉下来
                        dropped += take;
                    }
                }
                remaining -= take;
            }
            target.sendMessage(Text.of("[AI] 已处理物品: " + itemId + " x" + n + " (发放=" + given + ", 掉落=" + dropped + ")"));
            result.set("ok: gave=" + given + ", dropped=" + dropped);
        });

        return result.get();
    }

    /* ===================== 工具内部辅助 ===================== */

    private ServerPlayerEntity findPlayer(String nameOrUuid) {
        // 先按名称
        ServerPlayerEntity byName = server.getPlayerManager().getPlayer(nameOrUuid);
        if (byName != null) return byName;

        // 再按 UUID
        try {
            UUID u = UUID.fromString(nameOrUuid);
            return server.getPlayerManager().getPlayer(u);
        } catch (Exception ignore) {
            return null;
        }
    }

    private void runOnMainAndWait(Runnable task) {
        if (server.isOnThread()) { // 已在主线程
            task.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        server.execute(() -> {
            try { task.run(); } finally { latch.countDown(); }
        });
        try { latch.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
