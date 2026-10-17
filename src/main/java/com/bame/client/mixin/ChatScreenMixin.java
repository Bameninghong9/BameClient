package com.bame.client.mixin;

import com.bame.client.playerwheel.PlayerWheel;
import com.bame.client.render.SpotifyHudRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat offen: Spieler-Auswahlrad ({@link PlayerWheel}) und Linksklicks auf die Steuerknöpfe des Spotify-HUD.
 * Getroffene Klicks gehen nicht an den Chat; alle anderen (Links, Text, Eingabezeile) laufen unverändert weiter.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    ChatInputSuggestor chatInputSuggestor;

    @Unique
    private boolean caeser$suggestionsOpen() {
        return this.chatInputSuggestor != null && this.chatInputSuggestor.isOpen();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void caeser$wheelAndSpotifyClicks(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        // Rad zuerst: es liegt über allem anderen
        if (PlayerWheel.mouseClicked((ChatScreen) (Object) this, click.x(), click.y(), click.button(), caeser$suggestionsOpen())) {
            cir.setReturnValue(true);
            return;
        }
        if (click.button() == 0 && SpotifyHudRenderer.handleClick(click.x(), click.y())) {
            cir.setReturnValue(true);
        }
    }

    /** Esc schließt zuerst ein offenes Rad, erst der nächste Esc den Chat. */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void caeser$wheelEscape(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (PlayerWheel.keyPressed(input.key())) cir.setReturnValue(true);
    }

    /** Markierung und Rad über Chat, Eingabezeile und Vorschlägen. */
    @Inject(method = "render", at = @At("TAIL"))
    private void caeser$renderPlayerWheel(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        PlayerWheel.render((ChatScreen) (Object) this, context, mouseX, mouseY, caeser$suggestionsOpen());
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void caeser$resetPlayerWheel(CallbackInfo ci) {
        PlayerWheel.reset();
    }
}
