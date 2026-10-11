package com.bame.client.mixin;

import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screen.StatsScreen$ItemStatsListWidget$StatEntry")
public interface StatsItemStatEntryAccessor {
    @Accessor("item")
    Item caeser$getItem();
}
