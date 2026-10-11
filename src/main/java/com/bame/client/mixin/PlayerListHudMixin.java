package com.bame.client.mixin;

import com.bame.client.cosmetics.CaeserPlayerSyncManager;
import com.bame.client.module.NameProtectModule;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void onGetPlayerName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        Text original = cir.getReturnValue();
        if (original == null) return;

        boolean modified = false;
        if (NameProtectModule.enabled) {
            original = NameProtectModule.protect(original);
            modified = true;
        }

        String username = (entry != null && entry.getProfile() != null) ? entry.getProfile().name() : null;
        if (username != null && CaeserPlayerSyncManager.isCaeserPlayer(username)) {
            String badge = CaeserPlayerSyncManager.getPlayerBadgePrefix(username);
            if (badge != null && !badge.isEmpty()) {
                original = Text.literal(badge).append(original);
                modified = true;
            }
        }

        if (modified) {
            cir.setReturnValue(original);
        }
    }
}
