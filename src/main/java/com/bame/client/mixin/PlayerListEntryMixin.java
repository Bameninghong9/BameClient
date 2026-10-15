package com.bame.client.mixin;

import com.bame.client.cosmetics.CaeserCapeManager;
import com.bame.client.cosmetics.CaeserSkinManager;
import com.bame.client.module.SkinProtectModule;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {
    @Shadow public abstract GameProfile getProfile();

    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void onGetSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && this.getProfile() != null && mc.player.getUuid().equals(this.getProfile().id())) {
            if (SkinProtectModule.enabled) {
                // Caeser-Cape des eigenen Spielers bleibt auch mit Skin-Protect sichtbar
                cir.setReturnValue(CaeserCapeManager.apply(this.getProfile().id(), SkinProtectModule.getCurrentSkin()));
            } else if (CaeserSkinManager.hasActiveOverride()) {
                SkinTextures custom = CaeserSkinManager.getActiveSkinTextures();
                if (custom != null) {
                    cir.setReturnValue(CaeserCapeManager.apply(this.getProfile().id(), custom));
                }
            }
        }
    }

    /** Alle übrigen Fälle (fremde Spieler, eigener Account-Skin): Caeser-Cape einsetzen, falls vorhanden und geladen. */
    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void caeser$applyCape(CallbackInfoReturnable<SkinTextures> cir) {
        GameProfile profile = this.getProfile();
        if (profile == null) return;
        com.bame.client.util.Prof.push("caeser_cape_apply");
        try {
            SkinTextures base = cir.getReturnValue();
            SkinTextures withCape = CaeserCapeManager.apply(profile.id(), base);
            if (withCape != base) cir.setReturnValue(withCape);
        } finally {
            com.bame.client.util.Prof.pop();
        }
    }
}
