package com.bame.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Comparator;

@Mixin(targets = "net.minecraft.client.gui.screen.StatsScreen$ItemStatsListWidget")
public interface StatsItemListAccessor {
    /** The vanilla column comparator (uses the currently selected column and sort order). */
    @Accessor("comparator")
    Comparator<?> caeser$getComparator();
}
