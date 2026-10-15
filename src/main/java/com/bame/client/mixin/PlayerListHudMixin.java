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
        com.bame.client.util.Prof.push("caeser_tab_badge");
        try {
            caeser$decorate(entry, cir);
        } finally {
            com.bame.client.util.Prof.pop();
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static void caeser$decorate(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        Text original = cir.getReturnValue();
        if (original == null) return;

        boolean modified = false;
        if (NameProtectModule.enabled) {
            original = NameProtectModule.protect(original);
            modified = true;
        }

        String username = (entry != null && entry.getProfile() != null) ? entry.getProfile().name() : null;
        java.util.UUID uuid = (entry != null && entry.getProfile() != null) ? entry.getProfile().id() : null;
        // Pro Eintrag und Frame: eine kombinierte Abfrage statt isCaeserPlayer + getPlayerBadgePrefix
        if ((uuid != null || username != null) && CaeserPlayerSyncManager.showsTabBadge(uuid, username)) {
            original = com.bame.client.module.TabBadgeModule.badgeText().append(original);
            modified = true;
        }

        if (modified) {
            cir.setReturnValue(original);
        }
    }
}
