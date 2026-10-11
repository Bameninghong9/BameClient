package com.bame.client.gui.search;

import net.minecraft.client.gui.widget.EntryListWidget;

/** Implemented (via mixin) by the package-private {@code StatsScreen.StatsTab}: gives access to its list. */
public interface StatsTabAccess {
    EntryListWidget<?> caeser$getList();
}
