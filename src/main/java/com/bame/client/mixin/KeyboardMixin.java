package com.bame.client.mixin;

import com.bame.client.gui.search.ContainerSearch;
import com.bame.client.util.NarratorGuard;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Klammert {@code Keyboard.onKey(long, int, KeyInput)} für {@link NarratorGuard}: Strg+B soll die Vanilla-Sprachausgabe
 * nicht umschalten, wenn B im Spiel eine Caeser-Taste ist (Emote-Rad). Das Flag gilt nur während dieses Aufrufs;
 * {@code SimpleOptionMixin} verwirft solange Änderungen der Narrator-Option. {@code require = 0}: Fehlt der
 * Einhängepunkt in einer anderen Version, bleibt nur das Vanilla-Verhalten (kein Absturz).
 *
 * <p>Außerdem {@code Keyboard.onChar}: Ist die Suchleiste unter einem Container fokussiert ({@link ContainerSearch}),
 * gehen getippte Zeichen direkt dorthin (auch wenn im Inventar das Rezeptbuch ein eigenes Suchfeld hat).
 */
@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), require = 0)
    private void caeser$narratorGuardBegin(long window, int action, KeyInput input, CallbackInfo ci) {
        NarratorGuard.beginKey(action, input);
    }

    @Inject(method = "onKey", at = @At("RETURN"), require = 0)
    private void caeser$narratorGuardEnd(long window, int action, KeyInput input, CallbackInfo ci) {
        NarratorGuard.endKey();
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true, require = 0)
    private void caeser$containerSearchChar(long window, CharInput input, CallbackInfo ci) {
        if (ContainerSearch.handleCharTyped(window, input)) ci.cancel();
    }
}
