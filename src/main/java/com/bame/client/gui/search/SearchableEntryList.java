package com.bame.client.gui.search;

/** Implemented (via mixin) by entry lists that remember their full entry list and can show a filtered view. */
public interface SearchableEntryList {
    void caeser$applySearch(String query);
}
