package com.bame.client.gui.search;

import com.bame.client.mixin.ControlsListWidgetKeyBindingEntryAccessor;
import net.minecraft.client.gui.screen.option.ControlsListWidget;
import net.minecraft.client.option.KeyBinding;

import java.util.ArrayList;
import java.util.List;

/** Filter logic for the vanilla Key Binds list ({@link ControlsListWidget}). */
public final class KeybindSearch {
    /** Header height of the Key Binds screen with title + search field (vanilla: 33). */
    public static final int HEADER_HEIGHT = 50;
    public static final int HEADER_SPACING = 4;

    private KeybindSearch() {}

    /**
     * A key binding is shown if the query matches its name, its currently bound key or its category.
     * Category headers are only kept if at least one entry below them is visible.
     */
    public static List<ControlsListWidget.Entry> filter(List<ControlsListWidget.Entry> all, SearchMatcher matcher) {
        if (matcher.isEmpty()) return new ArrayList<>(all);

        List<ControlsListWidget.Entry> result = new ArrayList<>();
        ControlsListWidget.Entry pendingHeader = null;
        for (ControlsListWidget.Entry entry : all) {
            if (entry instanceof ControlsListWidgetKeyBindingEntryAccessor keyEntry) {
                if (matches(keyEntry, matcher)) {
                    if (pendingHeader != null) {
                        result.add(pendingHeader);
                        pendingHeader = null;
                    }
                    result.add(entry);
                }
            } else {
                // Category header (or an unknown entry type): only shown together with a visible entry
                pendingHeader = entry;
            }
        }
        return result;
    }

    private static boolean matches(ControlsListWidgetKeyBindingEntryAccessor entry, SearchMatcher matcher) {
        KeyBinding binding = entry.caeser$getBinding();
        if (matcher.matches(entry.caeser$getBindingName())) return true;
        if (binding == null) return false;
        if (matcher.matches(binding.getBoundKeyLocalizedText())) return true;
        return binding.getCategory() != null && matcher.matches(binding.getCategory().getLabel());
    }
}
