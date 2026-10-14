package com.bame.client.mixin;

import com.bame.client.render.SpotifyHudRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Linksklicks bei offenem Chat auf die Steuerknöpfe des Spotify-HUD; getroffene Klicks gehen nicht an den Chat. */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void caeser$spotifyControls(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() == 0 && SpotifyHudRenderer.handleClick(click.x(), click.y())) {
            cir.setReturnValue(true);
        }
    }
}
