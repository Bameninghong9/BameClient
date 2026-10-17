package com.bame.client.gui.search;

import java.util.Arrays;

/**
 * Treffer-Cache der Container-Suche pro Slot (reine Java-Logik, getestet in {@code src/plannerTest}).
 *
 * <p>Zwei Stufen:
 * <ol>
 *     <li><b>Eintrag</b> ({@link ItemSearchEntry}, normalisierte Texte): hängt nur am Slot-Inhalt. Neu gebaut, wenn
 *     im Slot eine andere {@code ItemStack}-Instanz liegt, sich die gemerkte Inhalts-Referenz (Shulker-/Bundle-
 *     Komponente, kann sich ohne neue Instanz ändern) ändert oder die Such-Einstellungen wechseln
 *     ({@link #invalidateEntries()}).</li>
 *     <li><b>Treffer</b>: hängt zusätzlich am Suchtext. Ein neuer Suchtext ({@link #invalidateMatches()}) prüft nur
 *     die fertigen Einträge neu, ohne Texte erneut aufzubereiten.</li>
 * </ol>
 * Pro Frame wird nur verglichen (Referenzen, Zähler), nichts angelegt. Leere Slots behandelt der Aufrufer.
 */
public final class SlotMatchCache {
    public static final byte UNKNOWN = 0;
    public static final byte MATCH = 1;
    public static final byte NO_MATCH = 2;

    private Object[] stacks = new Object[0];
    private Object[] contentRefs = new Object[0];
    private Object[] entries = new Object[0];
    private int[] entryGen = new int[0];
    private int[] matchGen = new int[0];
    private byte[] states = new byte[0];
    private int entryGeneration = 1;
    private int matchGeneration = 1;

    /** Platz für {@code slots} Slots (wächst nur; neue Plätze gelten als ungültig). */
    public void ensureCapacity(int slots) {
        if (slots <= this.states.length) return;
        int n = Math.max(slots, this.states.length + 16);
        this.stacks = Arrays.copyOf(this.stacks, n);
        this.contentRefs = Arrays.copyOf(this.contentRefs, n);
        this.entries = Arrays.copyOf(this.entries, n);
        this.entryGen = Arrays.copyOf(this.entryGen, n);
        this.matchGen = Arrays.copyOf(this.matchGen, n);
        this.states = Arrays.copyOf(this.states, n);
    }

    public int capacity() {
        return this.states.length;
    }

    /** Such-Einstellungen geändert (Lore/Inhalt): alle Einträge und Treffer neu. */
    public void invalidateEntries() {
        this.entryGeneration++;
        this.matchGeneration++;
    }

    /** Suchtext geändert: nur die Treffer neu (Einträge bleiben). */
    public void invalidateMatches() {
        this.matchGeneration++;
    }

    /** Muss der Eintrag für diesen Slot (neu) gebaut werden? */
    public boolean needsEntry(int slot, Object stack) {
        return this.entryGen[slot] != this.entryGeneration || this.stacks[slot] != stack;
    }

    /** Gemerkte Inhalts-Referenz (nur bei Container-Items gesetzt, sonst {@code null}). */
    public Object contentRef(int slot) {
        return this.contentRefs[slot];
    }

    public void putEntry(int slot, Object stack, Object contentRef, Object entry) {
        this.stacks[slot] = stack;
        this.contentRefs[slot] = contentRef;
        this.entries[slot] = entry;
        this.entryGen[slot] = this.entryGeneration;
        this.matchGen[slot] = 0; // Treffer neu prüfen
        this.states[slot] = UNKNOWN;
    }

    public Object entry(int slot) {
        return this.entries[slot];
    }

    public boolean needsMatch(int slot) {
        return this.matchGen[slot] != this.matchGeneration;
    }

    public void putMatch(int slot, boolean match) {
        this.states[slot] = match ? MATCH : NO_MATCH;
        this.matchGen[slot] = this.matchGeneration;
    }

    /** {@link #MATCH}, {@link #NO_MATCH} oder {@link #UNKNOWN} (noch nicht geprüft). */
    public byte state(int slot) {
        return slot >= 0 && slot < this.states.length ? this.states[slot] : UNKNOWN;
    }

    /** Alle Referenzen freigeben (Screen geschlossen). */
    public void clear() {
        Arrays.fill(this.stacks, null);
        Arrays.fill(this.contentRefs, null);
        Arrays.fill(this.entries, null);
        Arrays.fill(this.entryGen, 0);
        Arrays.fill(this.matchGen, 0);
        Arrays.fill(this.states, UNKNOWN);
    }
}
