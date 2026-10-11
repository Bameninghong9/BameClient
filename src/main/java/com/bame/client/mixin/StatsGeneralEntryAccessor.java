package com.bame.client.mixin;

import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screen.StatsScreen$GeneralStatsListWidget$Entry")
public interface StatsGeneralEntryAccessor {
    @Accessor("displayName")
    Text caeser$getDisplayName();
}
