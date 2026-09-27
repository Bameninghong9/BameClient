package com.bame.client.mixin;

import com.bame.client.module.NameProtectModule;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Team.class)
public class TeamMixin {
    @Inject(method = "decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;", at = @At("RETURN"), cancellable = true)
    private static void onDecorateName(AbstractTeam team, Text name, CallbackInfoReturnable<MutableText> cir) {
        if (NameProtectModule.enabled && cir.getReturnValue() != null) {
            cir.setReturnValue((MutableText) NameProtectModule.protect(cir.getReturnValue()));
        }
    }
}
