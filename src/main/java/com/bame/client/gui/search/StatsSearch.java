package com.bame.client.gui.search;

import com.bame.client.mixin.StatsEntityEntryAccessor;
import com.bame.client.mixin.StatsGeneralEntryAccessor;
import com.bame.client.mixin.StatsItemListAccessor;
import com.bame.client.mixin.StatsItemStatEntryAccessor;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Filter logic for the three Statistics lists (General / Items / Mobs).
 * Each list gets a {@link Snapshot} of its complete entry list; the filtered view is set via {@code replaceEntries}.
 */
public final class StatsSearch {
    /** How much further down the lists start, to make room for the search field below the tab bar. */
    public static final int LIST_OFFSET = 22;
    /** Gap between the bottom of the tab bar and the search field. */
    public static final int FIELD_GAP = 3;

    private StatsSearch() {}

    /** Displayed name of a stats entry, or {@code null} for entries without a name (the Items column header). */
    static String displayName(Object entry) {
        Text text = null;
        if (entry instanceof StatsGeneralEntryAccessor general) {
            text = general.caeser$getDisplayName();
        } else if (entry instanceof StatsEntityEntryAccessor entity) {
            text = entity.caeser$getEntityTypeName();
        } else if (entry instanceof StatsItemStatEntryAccessor itemEntry) {
            Item item = itemEntry.caeser$getItem();
            text = item == null ? null : item.getDefaultStack().getName();
            if (text == null) return "";
        } else {
            return null;
        }
        return text == null ? "" : text.getString();
    }

    /** Complete entry list of one statistics list. */
    public static final class Snapshot {
        private final EntryListWidget<?> list;
        private final List<Object> entries = new ArrayList<>();
        /** Entry -> displayed name ({@code null} for the header). */
        private final Map<Object, String> names = new IdentityHashMap<>();

        public Snapshot(EntryListWidget<?> list) {
            this.list = list;
            this.sync();
        }

        public boolean hasEntries() {
            return !this.entries.isEmpty();
        }

        /** Picks up entries that are not known yet (nothing is ever lost, even if something adds entries later). */
        private void sync() {
            for (Object entry : this.list.children()) {
                if (!this.names.containsKey(entry)) {
                    this.names.put(entry, displayName(entry));
                    this.entries.add(entry);
                }
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void apply(SearchMatcher matcher) {
            this.sync();
            List<Object> headers = new ArrayList<>();
            List<Object> matches = new ArrayList<>();
            for (Object entry : this.entries) {
                String name = this.names.get(entry);
                if (name == null) {
                    headers.add(entry);
                } else if (matcher.matches(name)) {
                    matches.add(entry);
                }
            }

            // Items: keep the current column sorting (vanilla comparator; without a selected column it keeps the order)
            if (this.list instanceof StatsItemListAccessor itemList && itemList.caeser$getComparator() != null) {
                Comparator comparator = itemList.caeser$getComparator();
                matches.sort(comparator);
            }

            List<Object> result = new ArrayList<>(headers.size() + matches.size());
            if (!matches.isEmpty()) {
                // The header (sort buttons) is only shown together with at least one matching entry
                result.addAll(headers);
                result.addAll(matches);
            }
            EntryListSearch.showEntries(this.list, result);
        }
    }

    /** Keeps one snapshot per list; lists that are new (built/rebuilt by vanilla) get a fresh snapshot. */
    public static final class Snapshots {
        private final Map<EntryListWidget<?>, Snapshot> byList = new IdentityHashMap<>();

        /** @return {@code true} if at least one list was not known yet */
        public boolean update(List<EntryListWidget<?>> lists) {
            Set<EntryListWidget<?>> current = Collections.newSetFromMap(new IdentityHashMap<>());
            current.addAll(lists);
            this.byList.keySet().removeIf(list -> !current.contains(list));
            boolean added = false;
            for (EntryListWidget<?> list : lists) {
                if (!this.byList.containsKey(list)) {
                    this.byList.put(list, new Snapshot(list));
                    added = true;
                }
            }
            return added;
        }

        public void applyAll(String query) {
            SearchMatcher matcher = SearchMatcher.of(query);
            for (Snapshot snapshot : this.byList.values()) {
                snapshot.apply(matcher);
            }
        }

        public boolean hasEntries(EntryListWidget<?> list) {
            Snapshot snapshot = this.byList.get(list);
            return snapshot != null && snapshot.hasEntries();
        }
    }
}
