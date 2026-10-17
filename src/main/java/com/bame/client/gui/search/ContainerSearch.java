package com.bame.client.gui.search;

import com.bame.client.BameClient;
import com.bame.client.gui.GuiTheme;
import com.bame.client.mixin.HandledScreenAccessor;
import com.bame.client.module.ContainerSearchModule;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HopperScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * „Container Search“ (Mod-Menü → Misc, {@link ContainerSearchModule}): Suchleiste unter Truhe/großer Truhe/Fass/
 * Endertruhe ({@link GenericContainerScreen}, also auch Server-Menüs im Truhen-GUI), {@link ShulkerBoxScreen},
 * {@link HopperScreen}, {@link Generic3x3ContainerScreen} (Spender/Werfer) und optional im eigenen
 * {@link InventoryScreen}; nie im Kreativ-Inventar. Nicht passende Slots (Container und Spieler-Inventar) bekommen ein
 * dunkles, halbtransparentes Overlay, passende optional einen dezenten Rahmen in der Akzentfarbe.
 *
 * <p>Einhängepunkte:
 * <ul>
 *     <li>{@code ScreenEvents.BEFORE_INIT/AFTER_INIT}: Feld als Kind-Widget einfügen (auch nach Größenänderung,
 *     Text und Fokus bleiben), Screen-Events registrieren.</li>
 *     <li>{@code ScreenKeyboardEvents.allowKeyPress}: läuft vor {@code Screen.keyPressed}. Solange das Feld fokussiert
 *     ist, gehen Tasten nur ans Feld (keine Inventar-Aktionen), Esc schließt wie Vanilla, Enter/Tab entfokussieren,
 *     Strg+F fokussiert ({@link ContainerSearchKeys}).</li>
 *     <li>{@code KeyboardMixin} ({@code Keyboard.onChar}): getippte Zeichen direkt ans fokussierte Feld (auch wenn das
 *     Rezeptbuch im Inventar ein eigenes Suchfeld hat).</li>
 *     <li>{@code ScreenMouseEvents}: Klicks aufs Feld fokussieren/leeren und erreichen den Screen nie (sonst würde
 *     Vanilla außerhalb des Fensters ein Item am Mauszeiger fallen lassen). Klicks auf Slots bleiben unverändert und
 *     lassen den Fokus, Klicks außerhalb von Feld und Fenster entfokussieren.</li>
 *     <li>{@code HandledScreenMixin}: Overlay direkt nach {@code drawSlots} (über Items und Stapelzahl, unter
 *     Hover-Markierung, Mauszeiger-Item und Tooltip).</li>
 *     <li>{@code InvMoveModule.shouldMove}: Inv Move ruht, solange {@link #isTyping()}.</li>
 * </ul>
 *
 * <p>Leistung: Treffer pro Slot im {@link SlotMatchCache} (neu nur bei geändertem Suchtext oder Slot-Inhalt), die
 * Zeichenschleife legt keine Objekte an. Profiler-Abschnitt {@code caeser_container_search}.
 *
 * <p>Suchtext: beim Schließen geleert („Remember search“ merkt ihn bis zum nächsten Öffnen). Ersetzt der Server das
 * Fenster direkt durch ein neues (Seitenwechsel in Shops, Auffrischen von Menüs), bleiben Text und Fokus erhalten.
 */
public final class ContainerSearch {
    /** Dunkles, halbtransparentes Overlay für nicht passende Items (Item bleibt schemenhaft erkennbar). */
    private static final int DIM_COLOR = 0xB00E1016;
    /** Deckkraft des Treffer-Rahmens (Akzentfarbe). */
    private static final int FRAME_ALPHA = 0xD0;
    /** Verschachtelung beim Durchsuchen von Inhalt: Shulker → Bundle → Items. */
    private static final int CONTENT_DEPTH = 2;
    private static final String PROFILER_SECTION = "caeser_container_search";

    private static Session session;
    /** „Remember search“: Text der zuletzt geschlossenen Suche. */
    private static String rememberedQuery = "";
    /** Text/Fokus des eben entfernten Fensters; gilt nur, wenn direkt (ohne Rückkehr ins Spiel) ein neues folgt. */
    private static String carryQuery = null;
    private static boolean carryFocus = false;
    private static boolean errorLogged = false;

    private ContainerSearch() {}

    /** Einmal beim Client-Start ({@code BameClient}). */
    public static void register() {
        ScreenEvents.BEFORE_INIT.register(ContainerSearch::beforeInit);
        ScreenEvents.AFTER_INIT.register(ContainerSearch::afterInit);
    }

    /** Bekommt dieser Screen eine Suchleiste? */
    public static boolean isCandidate(Screen screen) {
        if (screen == null || screen instanceof CreativeInventoryScreen) return false;
        if (screen instanceof GenericContainerScreen || screen instanceof ShulkerBoxScreen
                || screen instanceof HopperScreen || screen instanceof Generic3x3ContainerScreen) {
            return true;
        }
        return ContainerSearchModule.playerInventory && screen instanceof InventoryScreen;
    }

    /** Wird gerade ins Suchfeld getippt? (Inv Move ruht dann.) Billig genug für {@code KeyBinding.isPressed}. */
    public static boolean isTyping() {
        Session s = session;
        if (s == null || !s.visible || !s.field.isFocused()) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.currentScreen == s.screen;
    }

    /** Pro Client-Tick: nach Rückkehr ins Spiel gilt kein übernommener Suchtext mehr. */
    public static void tick(MinecraftClient client) {
        if (client.currentScreen == null) {
            carryQuery = null;
            carryFocus = false;
        }
        Session s = session;
        if (s != null && client.currentScreen != s.screen) {
            // Rückfallebene, falls das Entfernen-Event fehlte: Referenzen freigeben
            s.dispose();
            session = null;
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Screen-Lebenszyklus
    // ------------------------------------------------------------------------------------------------------------

    private static void beforeInit(MinecraftClient client, Screen screen, int width, int height) {
        Session s = session;
        // Größenänderung: Vanilla nimmt beim Neuaufbau den Fokus weg; danach wieder setzen
        if (s != null && s.screen == screen) s.refocus = s.field.isFocused();
    }

    private static void afterInit(MinecraftClient client, Screen screen, int width, int height) {
        if (client == null || client.currentScreen != screen) return;
        Profiler profiler = Profilers.get();
        profiler.push(PROFILER_SECTION);
        try {
            Session s = session;
            if (!ContainerSearchModule.enabled || !isCandidate(screen)) {
                if (s != null && s.screen == screen) {
                    s.dispose();
                    session = null;
                }
                carryQuery = null;
                carryFocus = false;
                return;
            }
            HandledScreen<?> handled = (HandledScreen<?>) screen;
            if (s == null || s.screen != handled) {
                if (s != null) s.dispose();
                String initial;
                boolean focus = false;
                if (carryQuery != null) {
                    initial = carryQuery;
                    focus = carryFocus;
                } else {
                    initial = ContainerSearchModule.rememberSearch ? rememberedQuery : "";
                }
                carryQuery = null;
                carryFocus = false;
                s = new Session(handled, initial);
                s.refocus = focus;
                session = s;
            }
            s.attach();
        } catch (Throwable t) {
            logOnce(t);
        } finally {
            profiler.pop();
        }
    }

    private static void onRemove(Screen screen) {
        Session s = session;
        if (s == null || s.screen != screen) return;
        String text = s.field.getText();
        boolean focused = s.field.isFocused();
        rememberedQuery = ContainerSearchModule.rememberSearch ? text : "";
        if (!text.isEmpty() || focused) {
            carryQuery = text;
            carryFocus = focused;
        } else {
            carryQuery = null;
            carryFocus = false;
        }
        s.dispose();
        session = null;
    }

    private static Session sessionFor(Screen screen) {
        Session s = session;
        return s != null && s.screen == screen && ContainerSearchModule.enabled ? s : null;
    }

    private static void beforeRender(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        Session s = sessionFor(screen);
        if (s == null) return;
        Profiler profiler = Profilers.get();
        profiler.push(PROFILER_SECTION);
        try {
            s.updateLayout();
            s.refresh();
        } catch (Throwable t) {
            logOnce(t);
        } finally {
            profiler.pop();
        }
    }

    private static void afterRender(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        Session s = sessionFor(screen);
        if (s == null) return;
        // Nicht gezeichnet (z. B. Rezeptbuch im schmalen Inventar verdeckt alles): kein Tippen ins Unsichtbare
        s.visible = s.field.consumeRendered();
        if (!s.visible && s.field.isFocused()) s.unfocus();
        s.refreshedThisFrame = false;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Eingabe
    // ------------------------------------------------------------------------------------------------------------

    private static boolean allowKeyPress(Screen screen, KeyInput input) {
        Session s = sessionFor(screen);
        if (s == null) return true;
        try {
            switch (ContainerSearchKeys.decide(s.field.isFocused(), s.visible, input.key(), input.modifiers())) {
                case ContainerSearchKeys.FOCUS -> {
                    s.focus();
                    return false;
                }
                case ContainerSearchKeys.UNFOCUS -> {
                    s.unfocus();
                    return false;
                }
                case ContainerSearchKeys.UNFOCUS_PASS -> {
                    s.unfocus();
                    return true;
                }
                case ContainerSearchKeys.EDIT -> {
                    s.field.keyPressed(input);
                    return false;
                }
                default -> {
                    return true;
                }
            }
        } catch (Throwable t) {
            logOnce(t);
            return true;
        }
    }

    /** Aus {@code KeyboardMixin} (Anfang von {@code Keyboard.onChar}): {@code true} = Zeichen verbraucht. */
    public static boolean handleCharTyped(long window, CharInput input) {
        Session s = session;
        if (s == null || !s.visible || !s.field.isFocused() || !ContainerSearchModule.enabled) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen != s.screen || client.getOverlay() != null) return false;
        if (client.getWindow() == null || client.getWindow().getHandle() != window) return false;
        try {
            s.field.charTyped(input);
        } catch (Throwable t) {
            logOnce(t);
        }
        return true;
    }

    private static boolean allowMouseClick(Screen screen, Click click) {
        Session s = sessionFor(screen);
        if (s == null || !s.visible) return true;
        try {
            int button = click.button();
            if (s.field.isMouseOver(click.x(), click.y())) {
                s.capture(button);
                if (button == 1 || (button == 0 && s.field.isOverClearButton(click.x(), click.y()))) {
                    // Rechtsklick oder "×": leeren und zum Weitertippen fokussieren
                    s.field.setText("");
                    s.focus();
                } else {
                    s.focus();
                    if (button == 0) s.field.mouseClicked(click, false); // Cursor an die Klickstelle
                }
                return false;
            }
            // Klicks auf Slots/ins Fenster lassen den Fokus; außerhalb von Feld und Fenster endet das Tippen
            if (s.field.isFocused() && !s.insideGui(click.x(), click.y())) s.unfocus();
            return true;
        } catch (Throwable t) {
            logOnce(t);
            return true;
        }
    }

    private static boolean allowMouseRelease(Screen screen, Click click) {
        Session s = sessionFor(screen);
        return s == null || !s.release(click.button());
    }

    private static boolean allowMouseDrag(Screen screen, Click click, double horizontalAmount, double verticalAmount) {
        Session s = sessionFor(screen);
        return s == null || !s.isCaptured(click.button());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Aus {@code HandledScreenMixin} direkt nach {@code drawSlots} (Matrix ist schon um das Fenster verschoben, also
     * Slot-Koordinaten). Keine Objekte pro Frame außer den Zeichenaufrufen selbst.
     */
    public static void renderSlotOverlay(HandledScreen<?> screen, DrawContext context) {
        Session s = session;
        if (s == null || s.screen != screen || !ContainerSearchModule.enabled) return;
        Profiler profiler = Profilers.get();
        profiler.push(PROFILER_SECTION);
        try {
            if (!s.refreshedThisFrame) s.refresh();
            if (s.query.isEmpty()) return;
            boolean highlight = ContainerSearchModule.highlightMatches;
            int frameColor = GuiTheme.alpha(GuiTheme.accent(), FRAME_ALPHA);
            List<Slot> slots = screen.getScreenHandler().slots;
            SlotMatchCache cache = s.cache;
            int n = Math.min(slots.size(), cache.capacity());
            for (int i = 0; i < n; i++) {
                Slot slot = slots.get(i);
                if (!slot.isEnabled() || slot.getStack().isEmpty()) continue;
                byte state = cache.state(i);
                if (state == SlotMatchCache.NO_MATCH) {
                    context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, DIM_COLOR);
                } else if (state == SlotMatchCache.MATCH && highlight) {
                    drawMatchFrame(context, slot.x, slot.y, frameColor);
                }
            }
        } catch (Throwable t) {
            logOnce(t);
        } finally {
            profiler.pop();
        }
    }

    /** 1-px-Rahmen auf dem 18x18-Slotfeld, Ecken ausgespart (wirkt abgerundet). */
    private static void drawMatchFrame(DrawContext context, int slotX, int slotY, int color) {
        int x0 = slotX - 1;
        int y0 = slotY - 1;
        int x1 = slotX + 17;
        int y1 = slotY + 17;
        context.fill(x0 + 1, y0, x1 - 1, y0 + 1, color);
        context.fill(x0 + 1, y1 - 1, x1 - 1, y1, color);
        context.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        context.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Item-Daten
    // ------------------------------------------------------------------------------------------------------------

    /** Inhalts-Komponente (Shulker-Box/Container oder Bundle), deren Instanz sich bei Änderungen am Inhalt ändert. */
    private static Object contentsRef(ItemStack stack) {
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) return container;
        return stack.get(DataComponentTypes.BUNDLE_CONTENTS);
    }

    /** Durchsuchbare Daten eines Stacks; {@code depth} = wie viele Ebenen Inhalt noch aufgenommen werden. */
    static ItemSearchEntry buildEntry(ItemStack stack, boolean extras, int depth) {
        String id = "";
        try {
            id = Registries.ITEM.getId(stack.getItem()).toString();
            String name = stack.getName().getString();
            Text custom = stack.getCustomName();
            String baseName = custom != null ? stack.getItemName().getString() : null;
            List<String> extraLines = extras ? extraLines(stack) : null;
            List<ItemSearchEntry> children = depth > 0 ? children(stack, extras, depth - 1) : null;
            return ItemSearchEntry.of(name, baseName, id, extraLines, children);
        } catch (Throwable t) {
            logOnce(t);
            return ItemSearchEntry.of("", null, id, null, null);
        }
    }

    private static List<String> extraLines(ItemStack stack) {
        List<String> out = new ArrayList<>();
        addEnchantments(stack.getEnchantments(), out);
        addEnchantments(stack.get(DataComponentTypes.STORED_ENCHANTMENTS), out);
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) out.add(line.getString());
        }
        return out;
    }

    private static void addEnchantments(ItemEnchantmentsComponent enchantments, List<String> out) {
        if (enchantments == null || enchantments.isEmpty()) return;
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments.getEnchantmentEntries()) {
            RegistryEntry<Enchantment> enchantment = entry.getKey();
            // Angezeigter Name mit Stufe ("Schärfe V") und die ID ("sharpness", englisch auch bei deutschem Spiel)
            out.add(Enchantment.getName(enchantment, entry.getIntValue()).getString());
            Optional<RegistryKey<Enchantment>> key = enchantment.getKey();
            if (key.isPresent()) out.add(key.get().getValue().getPath());
        }
    }

    private static List<ItemSearchEntry> children(ItemStack stack, boolean extras, int depth) {
        List<ItemSearchEntry> out = null;
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            for (ItemStack child : container.iterateNonEmpty()) {
                if (out == null) out = new ArrayList<>();
                if (out.size() >= ItemSearchEntry.MAX_CHILDREN) break;
                out.add(buildEntry(child, extras, depth));
            }
        }
        BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack child : bundle.iterate()) {
                if (child.isEmpty()) continue;
                if (out == null) out = new ArrayList<>();
                if (out.size() >= ItemSearchEntry.MAX_CHILDREN) break;
                out.add(buildEntry(child, extras, depth));
            }
        }
        return out;
    }

    private static String placeholder() {
        return EntryListSearch.isGerman() ? "Items suchen…" : "Search items…";
    }

    private static void logOnce(Throwable t) {
        if (errorLogged) return;
        errorLogged = true;
        BameClient.LOGGER.warn("Caeser Client: container search failed (further errors are not logged)", t);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Zustand eines offenen Container-Fensters
    // ------------------------------------------------------------------------------------------------------------

    private static final class Session {
        final HandledScreen<?> screen;
        final ContainerSearchField field;
        final SlotMatchCache cache = new SlotMatchCache();
        ItemSearchQuery query = ItemSearchQuery.EMPTY;
        int dataSignature = -1;
        /** Gedrückte Maustasten, deren Druck auf dem Feld begann (Loslassen/Ziehen geht dann nicht an Vanilla). */
        int capturedButtons = 0;
        boolean refocus = false;
        boolean visible = true;
        boolean refreshedThisFrame = false;
        ContainerSearchLayout.Placement placement = null;
        int layoutScreenW, layoutScreenH, layoutGuiX, layoutGuiY, layoutGuiW, layoutGuiH;

        Session(HandledScreen<?> screen, String initialText) {
            this.screen = screen;
            this.field = new ContainerSearchField(placeholder());
            this.field.setChangedListener(this::onTextChanged);
            if (initialText != null && !initialText.isEmpty()) this.field.setText(initialText);
        }

        /** Nach jedem {@code init} des Screens (auch Größenänderung): Feld und Events neu einhängen. */
        void attach() {
            Screens.getButtons(this.screen).add(this.field);
            ScreenKeyboardEvents.allowKeyPress(this.screen).register(ContainerSearch::allowKeyPress);
            ScreenMouseEvents.allowMouseClick(this.screen).register(ContainerSearch::allowMouseClick);
            ScreenMouseEvents.allowMouseRelease(this.screen).register(ContainerSearch::allowMouseRelease);
            ScreenMouseEvents.allowMouseDrag(this.screen).register(ContainerSearch::allowMouseDrag);
            ScreenEvents.beforeRender(this.screen).register(ContainerSearch::beforeRender);
            ScreenEvents.afterRender(this.screen).register(ContainerSearch::afterRender);
            ScreenEvents.remove(this.screen).register(ContainerSearch::onRemove);
            this.cache.invalidateEntries(); // Sprache/Ressourcen können sich beim Neuaufbau geändert haben
            this.placement = null;
            this.updateLayout();
            this.visible = true;
            this.capturedButtons = 0;
            if (this.refocus) {
                this.refocus = false;
                this.focus();
            }
        }

        void dispose() {
            this.field.setFocused(false);
            this.cache.clear();
            this.capturedButtons = 0;
        }

        void onTextChanged(String text) {
            ItemSearchQuery parsed = ItemSearchQuery.parse(text);
            if (!parsed.normalized().equals(this.query.normalized())) {
                this.query = parsed;
                this.cache.invalidateMatches();
            }
        }

        void focus() {
            if (this.screen.getFocused() != this.field) this.screen.setFocused(this.field);
            this.field.setFocused(true);
        }

        void unfocus() {
            if (this.screen.getFocused() == this.field) this.screen.setFocused(null);
            this.field.setFocused(false);
        }

        void capture(int button) {
            if (button >= 0 && button < 31) this.capturedButtons |= 1 << button;
        }

        boolean isCaptured(int button) {
            return button >= 0 && button < 31 && (this.capturedButtons & (1 << button)) != 0;
        }

        /** @return {@code true}, wenn der Druck dieser Taste auf dem Feld begann (Loslassen dann verbrauchen) */
        boolean release(int button) {
            boolean captured = this.isCaptured(button);
            if (captured) this.capturedButtons &= ~(1 << button);
            return captured;
        }

        boolean insideGui(double x, double y) {
            HandledScreenAccessor acc = (HandledScreenAccessor) this.screen;
            int gx = acc.caeser$getX();
            int gy = acc.caeser$getY();
            return x >= gx && y >= gy && x < gx + acc.caeser$getBackgroundWidth() && y < gy + acc.caeser$getBackgroundHeight();
        }

        /** Position nur neu rechnen, wenn sich Screen oder Fenster bewegt haben (z. B. Rezeptbuch auf/zu). */
        void updateLayout() {
            HandledScreenAccessor acc = (HandledScreenAccessor) this.screen;
            int sw = this.screen.width;
            int sh = this.screen.height;
            int gx = acc.caeser$getX();
            int gy = acc.caeser$getY();
            int gw = acc.caeser$getBackgroundWidth();
            int gh = acc.caeser$getBackgroundHeight();
            if (this.placement != null && sw == this.layoutScreenW && sh == this.layoutScreenH && gx == this.layoutGuiX
                    && gy == this.layoutGuiY && gw == this.layoutGuiW && gh == this.layoutGuiH) {
                return;
            }
            this.layoutScreenW = sw;
            this.layoutScreenH = sh;
            this.layoutGuiX = gx;
            this.layoutGuiY = gy;
            this.layoutGuiW = gw;
            this.layoutGuiH = gh;
            ContainerSearchLayout.Placement p = ContainerSearchLayout.place(sw, sh, gx, gy, gw, gh);
            this.placement = p;
            this.field.setX(p.x());
            this.field.setY(p.y());
            this.field.setWidth(p.width());
            this.field.setHeight(p.height());
        }

        /** Cache prüfen und Trefferzahl aktualisieren (pro Frame; teuer nur bei geändertem Text/Inhalt). */
        void refresh() {
            this.refreshedThisFrame = true;
            int signature = ContainerSearchModule.dataSignature();
            if (signature != this.dataSignature) {
                this.dataSignature = signature;
                this.cache.invalidateEntries();
            }
            if (this.query.isEmpty()) {
                this.field.setMatchCount(-1);
                return;
            }
            boolean extras = ContainerSearchModule.searchLoreEnchantments;
            boolean contents = ContainerSearchModule.searchShulkers;
            List<Slot> slots = this.screen.getScreenHandler().slots;
            int n = slots.size();
            this.cache.ensureCapacity(n);
            int count = 0;
            for (int i = 0; i < n; i++) {
                Slot slot = slots.get(i);
                if (!slot.isEnabled()) continue;
                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) continue;
                boolean rebuild = this.cache.needsEntry(i, stack);
                if (!rebuild) {
                    Object ref = this.cache.contentRef(i);
                    if (ref != null && ref != contentsRef(stack)) rebuild = true;
                }
                if (rebuild) {
                    this.cache.putEntry(i, stack, contents ? contentsRef(stack) : null,
                            buildEntry(stack, extras, contents ? CONTENT_DEPTH : 0));
                }
                if (this.cache.needsMatch(i)) {
                    this.cache.putMatch(i, this.query.matches((ItemSearchEntry) this.cache.entry(i), extras, contents));
                }
                if (this.cache.state(i) == SlotMatchCache.MATCH) count++;
            }
            this.field.setMatchCount(count);
        }
    }
}
