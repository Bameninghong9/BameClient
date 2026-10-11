package com.bame.client.mixin;

import com.bame.client.gui.search.KeybindSearchHost;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the Key Binds screen build its own header (title + search field); all other option screens are untouched. */
@Mixin(GameOptionsScreen.class)
public abstract class GameOptionsScreenMixin {
    @Inject(method = "initHeader", at = @At("HEAD"), cancellable = true)
    private void caeser$initSearchHeader(CallbackInfo ci) {
        if ((Object) this instanceof KeybindSearchHost host) {
            host.caeser$initSearchHeader();
            ci.cancel();
        }
    }
}
