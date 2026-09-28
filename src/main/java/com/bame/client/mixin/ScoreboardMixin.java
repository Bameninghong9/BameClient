package com.bame.client.mixin;

import com.bame.client.module.FakeScoreboardModule;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Scoreboard.class)
public class ScoreboardMixin {
    @Inject(method = "getObjectiveForSlot", at = @At("HEAD"), cancellable = true)
    private void onGetObjectiveForSlot(ScoreboardDisplaySlot slot, CallbackInfoReturnable<ScoreboardObjective> cir) {
        if (FakeScoreboardModule.enabled && slot != ScoreboardDisplaySlot.LIST && slot != ScoreboardDisplaySlot.BELOW_NAME) {
            cir.setReturnValue(null);
        }
    }
}
