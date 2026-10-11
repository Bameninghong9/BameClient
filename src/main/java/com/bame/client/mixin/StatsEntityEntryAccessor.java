package com.bame.client.mixin;

import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screen.StatsScreen$EntityStatsListWidget$Entry")
public interface StatsEntityEntryAccessor {
    @Accessor("entityTypeName")
    Text caeser$getEntityTypeName();
}
