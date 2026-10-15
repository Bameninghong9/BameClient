package com.bame.client.mixin;

import com.bame.client.module.FullbrightModule;
import com.bame.client.util.NarratorGuard;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimpleOption.class)
public class SimpleOptionMixin {
    /**
     * Fullbright: Gamma anheben. {@code SimpleOption.getValue()} läuft sehr oft pro Frame (alle Optionen, auch in
     * Vanilla- und Sodium-Code); {@code @ModifyReturnValue} statt {@code @Inject(cancellable)} legt dafür kein
     * CallbackInfoReturnable-Objekt pro Aufruf an. Ergebnis unverändert.
     */
    @ModifyReturnValue(method = "getValue", at = @At("RETURN"))
    private Object caeser$fullbrightGamma(Object original) {
        if (FullbrightModule.enabled && original instanceof Double) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null) {
                if ((Object) this == client.options.getGamma()) {
                    double current = (Double) original;
                    double target = FullbrightModule.intensity * 10.0; // 10.0 is very bright
                    if (target > current) {
                        return Math.max(current, target);
                    }
                }
            }
        }
        return original;
    }

    /**
     * Strg+B im Spiel, während B eine aktive Caeser-Taste ist (Emote-Rad): die Vanilla-Umschaltung der Sprachausgabe
     * verwerfen (Flag nur während {@code Keyboard.onKey}, siehe {@link NarratorGuard}). {@code setValue} läuft selten
     * (Optionen laden/ändern), nicht pro Frame. {@code require = 0}: ohne Einhängepunkt bleibt es beim Vanilla-Verhalten.
     */
    @Inject(method = "setValue", at = @At("HEAD"), cancellable = true, require = 0)
    private void caeser$blockNarratorHotkey(Object value, CallbackInfo ci) {
        if (NarratorGuard.shouldBlockNarratorChange(this)) {
            ci.cancel();
        }
    }
}
