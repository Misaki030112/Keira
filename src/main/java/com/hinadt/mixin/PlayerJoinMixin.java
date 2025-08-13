package com.hinadt.mixin;

import com.hinadt.AiMisakiMod;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerJoinMixin {
    
    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void onPlayerJoin(ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        // 延迟发送欢迎消息，确保玩家完全加载
        player.server.execute(() -> {
            try {
                Thread.sleep(2000); // 等待2秒确保玩家完全加载
                player.sendMessage(Text.of("§b=== AI Misaki Mod ===§r"));
                player.sendMessage(Text.of("§f🤖 欢迎，" + player.getName().getString() + "！"));
                player.sendMessage(Text.of("§f我是你的AI助手 Misaki，输入 §e'帮助'§f 查看功能列表"));
                player.sendMessage(Text.of("§f支持功能：物品给予、传送、建筑辅助、天气控制等"));
                player.sendMessage(Text.of("§f示例：§a给我钻石剑§f、§a我要去出生点§f、§a帮我建造城堡"));
                player.sendMessage(Text.of("§b==================§r"));
                
                AiMisakiMod.LOGGER.info("玩家 {} 加入服务器，已发送欢迎消息", player.getName().getString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}