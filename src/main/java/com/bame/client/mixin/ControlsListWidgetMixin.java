package com.bame.client.mixin;

import com.bame.client.gui.search.EntryListSearch;
import com.bame.client.gui.search.KeybindSearch;
import com.bame.client.gui.search.SearchMatcher;
import com.bame.client.gui.search.SearchableEntryList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.ControlsListWidget;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Key Binds search: remembers the complete entry list and shows a filtered view via {@code replaceEntries}.
 */
@Mixin(ControlsListWidget.class)
public abstract class ControlsListWidgetMixin implements SearchableEntryList {
    @Unique
    private final List<ControlsListWidget.Entry> caeser$allEntries = new ArrayList<>();

    @Unique
    private ControlsListWidget caeser$self() {
        return (ControlsListWidget) (Object) this;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void caeser$rememberAllEntries(KeybindsScreen parent, MinecraftClient client, CallbackInfo ci) {
        this.caeser$allEntries.clear();
        this.caeser$allEntries.addAll(this.caeser$self().children());
    }

    /**
     * Vanilla {@code update()} only refreshes the visible children; keep the hidden (filtered out) entries up to date
     * as well, so button texts / Reset state / conflict markers are correct as soon as they become visible again.
     * (The conflict check itself always runs against {@code options.allKeys}, i.e. all key bindings.)
     */
    @Inject(method = "updateChildren", at = @At("TAIL"))
    private void caeser$updateHiddenEntries(CallbackInfo ci) {
        List<ControlsListWidget.Entry> visible = this.caeser$self().children();
        if (visible.size() >= this.caeser$allEntries.size()) return;
        Set<ControlsListWidget.Entry> visibleSet = Collections.newSetFromMap(new IdentityHashMap<>());
        visibleSet.addAll(visible);
        for (ControlsListWidget.Entry entry : this.caeser$allEntries) {
            if (!visibleSet.contains(entry) && entry instanceof ControlsListWidgetKeyBindingEntryAccessor keyEntry) {
                keyEntry.caeser$update();
            }
        }
    }

    @Override
    public void caeser$applySearch(String query) {
        ControlsListWidget self = this.caeser$self();
        // Pick up entries added after construction (e.g. by other mods), so nothing is ever lost
        Set<ControlsListWidget.Entry> known = Collections.newSetFromMap(new IdentityHashMap<>());
        known.addAll(this.caeser$allEntries);
        for (ControlsListWidget.Entry entry : self.children()) {
            if (!known.contains(entry)) this.caeser$allEntries.add(entry);
        }
        EntryListSearch.showEntries(self, KeybindSearch.filter(this.caeser$allEntries, SearchMatcher.of(query)));
    }
}
