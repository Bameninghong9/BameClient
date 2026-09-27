package com.bame.client.mixin;

import com.bame.client.module.NameProtectModule;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScoreboardEntry.class)
public class ScoreboardEntryMixin {
    @Inject(method = "name", at = @At("RETURN"), cancellable = true)
    private void onName(CallbackInfoReturnable<Text> cir) {
        if (NameProtectModule.enabled && cir.getReturnValue() != null) {
            cir.setReturnValue(NameProtectModule.protect(cir.getReturnValue()));
        }
    }
}
