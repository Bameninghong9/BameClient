package com.bame.client.mixin;

import com.bame.client.gui.search.StatsSearch;
import com.bame.client.gui.search.StatsTabAccess;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Statistics tabs: the list starts a bit lower to make room for the search field below the tab bar. */
@Mixin(targets = "net.minecraft.client.gui.screen.StatsScreen$StatsTab")
public abstract class StatsTabMixin implements StatsTabAccess {
    @Shadow @Final protected EntryListWidget<?> widget;

    @Override
    public EntryListWidget<?> caeser$getList() {
        return this.widget;
    }

    /** Vanilla positions the list at the top of the tab area on every call; move it down afterwards. */
    @Inject(method = "refreshGrid", at = @At("TAIL"))
    private void caeser$makeRoomForSearchField(ScreenRect tabArea, CallbackInfo ci) {
        EntryListWidget<?> list = this.widget;
        int height = Math.max(0, list.getHeight() - StatsSearch.LIST_OFFSET);
        list.position(list.getWidth(), height, list.getX(), list.getY() + StatsSearch.LIST_OFFSET);
    }
}
