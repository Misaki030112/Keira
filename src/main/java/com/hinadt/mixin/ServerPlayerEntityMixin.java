package com.hinadt.mixin;

import com.hinadt.util.PlayerLanguageCache;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Listen to client options updates to refresh per-player language cache.
 * Method name is based on 1.21.8 Yarn: setClientOptions.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(method = "setClientOptions", at = @At("TAIL"))
    private void onOptionsUpdated(SyncedClientOptions clientOptions, CallbackInfo ci) {
        // Refresh language from current options (read via PlayerLocales)
        PlayerLanguageCache.update((ServerPlayerEntity) (Object) this);
    }

}

