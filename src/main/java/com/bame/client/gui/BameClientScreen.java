package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import com.bame.client.gui.tab.*;
import com.bame.client.module.*;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BameClientScreen extends Screen {
    public enum ViewMode {
        HUB,
        MODS,
        COSMETICS,
        THEME,
        SETTINGS
    }
    public static ViewMode currentView = ViewMode.HUB;
    public static final String[] CATEGORIES = {"Combat", "Movement", "Visuals", "World", "Misc"};
    // Der aktive Tab hat genau eine Quelle: BameClientConfig.selectedCategory (wird gespeichert und geladen)

    /**
     * Alle Tabs in Anzeige-Reihenfolge. Ein Tab-Objekt gehört über getCategory() zu genau einer Kategorie;
     * eine Kategorie kann mehrere Tab-Objekte haben (z. B. Visuals = RenderTab + optische Combat-Module).
     */
    private final Map<String, ClientTab> tabs = new LinkedHashMap<>();
    private static final String COSMETICS_TAB = "Cosmetics";

    private boolean listeningMenuBind = false;
    private boolean scrollDragging = false;
    private boolean draggingSoundVolume = false;
    private boolean draggingHoverVolume = false;

    // HSV Color Picker Modal
    private boolean crosshairColorPickerOpen = false;
    private boolean hitColorColorPickerOpen = false;
    private boolean hitboxColorPickerOpen = false;
    private boolean blockOutlineColorPickerOpen = false;
    // Allgemeiner Farbwähler (Custom Fire, Custom Sky, Nametags): Titel, Lesen und Schreiben kommen vom Aufrufer
    private boolean genericColorPickerOpen = false;
    private String genericPickerTitle = "COLOR PICKER";
    private java.util.function.IntSupplier genericPickerGet = () -> 0xFFFFFFFF;
    private java.util.function.IntConsumer genericPickerSet = color -> {};
    private float cpHue = 0f, cpSat = 0f, cpVal = 1f;
    private int cpDrag = -1; // 0=sv, 1=hue
    private int cpX, cpY;

    // Item Selection Modal
    private boolean itemModalOpen = false;
    private int itemModalType = 0; // 0 = BLOCKS, 1 = TOOLS
    private final Set<String> tempSelectedItems = new LinkedHashSet<>();
    private final Map<String, Integer> tempToolThresholds = new LinkedHashMap<>();
    private final Map<String, CustomTextFieldWidget> toolThresholdFields = new LinkedHashMap<>();
    private String itemSearch = "";
    private CustomTextFieldWidget itemSearchWidget;
    private double itemModalScroll = 0;
    private boolean itemScrollDragging = false;
    private List<Item> cachedFilteredItems = null;
    private static List<Item> ALL_ITEMS = null;
    private static List<Item> ALL_TOOLS = null;

    // Layout
    private double scroll, scrollGrab;
    private int px, py, pw, ph, sidebar, cx, cy, cw, ch;
    private CustomTextFieldWidget search;
    private long openTime = 0;
    private final ThemeSettingsPanel themeSettings = new ThemeSettingsPanel();
    private final AmbientLighting ambient = new AmbientLighting();

    // 3D GUI: Lage des schwebenden Panels (Matrix des zuletzt gezeichneten Frames, Grundlage der Mausumrechnung)
    private final Panel3D panel3D = new Panel3D();
    private long panelOpenNanos = 0;
    private ViewMode lastRenderedView = null;

    public BameClientScreen() {
        super(Text.literal("Caeser Client"));
        com.bame.client.util.Prof.push("caeser_menu_ctor");
        try {
            tabs.put("Combat", new CombatTab(false));
            tabs.put("Movement", new MovementTab(false));
            tabs.put("Visuals", new RenderTab());
            tabs.put("VisualsCombat", new CombatTab(true));   // Reach Display, Hit Color, Low Shield, Hitboxes
            tabs.put("Misc", new MiscTab());
            tabs.put("MiscEmotes", new MovementTab(true));    // Animations & Emotes
            tabs.put("World", new WorldTab());
            tabs.put(COSMETICS_TAB, new CosmeticsTab());
            BameClientConfig.selectedCategory = BameClientConfig.sanitizeCategory(BameClientConfig.selectedCategory);
        } finally {
            com.bame.client.util.Prof.pop();
        }
    }

    /** Aktiver Tab des Mod-Menüs (einzige Quelle). */
    private static String activeCategory() {
        return BameClientConfig.selectedCategory;
    }

    /** Tab-Objekte, die in der Mod-Ansicht gerade gezeigt werden (bei Suche: alle). */
    private List<ClientTab> modTabsForView(String q) {
        List<ClientTab> out = new java.util.ArrayList<>();
        String cat = activeCategory();
        for (Map.Entry<String, ClientTab> e : tabs.entrySet()) {
            if (e.getKey().equals(COSMETICS_TAB)) continue;
            if (!q.isEmpty() || e.getValue().getCategory().equalsIgnoreCase(cat)) out.add(e.getValue());
        }
        return out;
    }

    /** Alle Mod-Tabs ohne Cosmetics. */
    private List<ClientTab> allModTabs() {
        List<ClientTab> out = new java.util.ArrayList<>();
        for (Map.Entry<String, ClientTab> e : tabs.entrySet()) {
            if (!e.getKey().equals(COSMETICS_TAB)) out.add(e.getValue());
        }
        return out;
    }

    private void cancelAllKeyListening() {
        for (ClientTab tab : tabs.values()) tab.cancelKeyListening();
    }

    /** Ist irgendwo ein Textfeld fokussiert? Dann werden keine Tastenkombinationen ausgewertet. */
    private boolean isAnyTextFieldFocused() {
        if (search != null && search.isFocused()) return true;
        if (itemSearchWidget != null && itemSearchWidget.isFocused()) return true;
        if (getFocused() instanceof CustomTextFieldWidget f && f.isFocused()) return true;
        if (CosmeticsTab.customSkinModalOpen && CosmeticsTab.modalPlayerField.isFocused()) return true;
        for (ClientTab tab : tabs.values()) {
            if (tab.hasFocusedTextField()) return true;
        }
        return false;
    }

    public void text(DrawContext c, String s, int x, int y, int color) {
        c.drawText(textRenderer, CustomGuiUtils.getFontText(s), x, y, color, false);
    }

    public void box(DrawContext c, int x, int y, int w, int h, int color) {
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, color, 6);
    }

    public void card(DrawContext c, int x, int y, int w, int h, boolean enabled) {
        int bg = enabled ? 0xFF161822 : 0xFF12141C;
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, bg, 6.0f);
        int border = enabled ? GuiTheme.accent() : 0xFF232733;
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, border, 6.0f);
    }

    public void moduleCard(DrawContext c, int x, int y, int w, int totalH,
                           boolean enabled, boolean expanded,
                           String title, String desc, String badge,
                           String iconType, int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, 50);
        int bg = enabled ? 0xFF161722 : (hover ? 0xFF171923 : 0xFF13141B);
        CustomGuiUtils.fillUltraRounded(c, x, y, w, totalH, bg, 5.0f);

        int border = enabled ? GuiTheme.accent() : (hover ? 0xFF35394A : 0xFF222531);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, totalH, border, 5.0f);

        int iconBg = enabled ? GuiTheme.alpha(GuiTheme.accent(), 42) : 0xFF1A1C25;
        int iconBorder = enabled ? GuiTheme.accent() : 0xFF272A37;
        CustomGuiUtils.fillUltraRounded(c, x + 9, y + 9, 32, 32, iconBg, 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, x + 9, y + 9, 32, 32, iconBorder, 4.0f);

        int iconColor = enabled ? GuiTheme.accent() : 0xFF8E95A4;
        switch (iconType) {
            case "combat" -> CustomGuiUtils.drawCombatIcon(c, x + 16, y + 16, iconColor);
            case "movement" -> CustomGuiUtils.drawMovementIcon(c, x + 16, y + 16, iconColor);
            case "visuals" -> CustomGuiUtils.drawVisualsIcon(c, x + 16, y + 16, iconColor);
            case "world" -> CustomGuiUtils.drawGlobeIcon(c, x + 16, y + 16, iconColor);
            case "misc" -> CustomGuiUtils.drawMiscIcon(c, x + 16, y + 16, iconColor);
            case "music" -> CustomGuiUtils.drawMusicIcon(c, x + 16, y + 16, iconColor);
            default -> CustomGuiUtils.drawCombatIcon(c, x + 16, y + 16, iconColor);
        }

        int titleY = y + 13;
        int descY = y + 27;

        String upperTitle = title.toUpperCase(java.util.Locale.ROOT);
        c.drawText(textRenderer, CustomGuiUtils.getFontText(upperTitle), x + 48, titleY, enabled ? 0xFFFFFFFF : 0xFFD4D8E0, false);

        int titleW = textRenderer.getWidth(CustomGuiUtils.getFontText(upperTitle));
        int chevX = x + 48 + titleW + 6;
        int chevY = titleY + 3;
        if (expanded) {
            c.fill(chevX, chevY + 2, chevX + 5, chevY + 3, GuiTheme.accent());
            c.fill(chevX + 1, chevY + 1, chevX + 4, chevY + 2, GuiTheme.accent());
            c.fill(chevX + 2, chevY, chevX + 3, chevY + 1, GuiTheme.accent());
        } else {
            // Beim Überfahren der Karte heller: die ganze Karte (außer dem Schalter) klappt die Einstellungen auf
            int chevCol = hover ? 0xFFFFFFFF : 0xFF656B7C;
            c.fill(chevX, chevY, chevX + 5, chevY + 1, chevCol);
            c.fill(chevX + 1, chevY + 1, chevX + 4, chevY + 2, chevCol);
            c.fill(chevX + 2, chevY + 2, chevX + 3, chevY + 3, chevCol);
        }

        int maxDescW = w - 90;
        String d = truncatedDescription(desc, maxDescW);
        c.drawText(textRenderer, CustomGuiUtils.getFontText(d), x + 48, descY, 0xFF8E95A4, false);

        int toggleX = x + w - 38;
        int toggleY = y + 17;
        CustomToggleWidget.draw(c, toggleX, toggleY, 26, 14, enabled);

        if (expanded) {
            c.fill(x + 8, y + 49, x + w - 8, y + 50, 0xFF242735);
        }
    }

    private record DescKey(String desc, int maxWidth) {}
    // Result of the description shortening per (desc, maxDescW); cleared in init() (resize, font reload)
    private final Map<DescKey, String> truncatedDescriptions = new java.util.HashMap<>();

    private String truncatedDescription(String desc, int maxDescW) {
        DescKey key = new DescKey(desc, maxDescW);
        String cached = truncatedDescriptions.get(key);
        if (cached != null) return cached;
        if (truncatedDescriptions.size() > 256) truncatedDescriptions.clear();

        String d = desc;
        if (textRenderer.getWidth(CustomGuiUtils.getFontText(d)) > maxDescW) {
            while (d.length() > 3 && textRenderer.getWidth(CustomGuiUtils.getFontText(d + "...")) > maxDescW) {
                d = d.substring(0, d.length() - 1);
            }
            d = d + "...";
        }
        truncatedDescriptions.put(key, d);
        return d;
    }

    public boolean handleCardHeaderClick(int mx, int my, int x, int y, int w,
                                         Runnable toggleEnabled, Runnable toggleExpanded) {
        int toggleX = x + w - 38;
        int toggleY = y + 17;
        if (inside(mx, my, toggleX - 4, toggleY - 4, 34, 22)) {
            toggleEnabled.run();
            com.bame.client.sound.ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }
        if (inside(mx, my, x, y, w, 50)) {
            toggleExpanded.run();
            com.bame.client.sound.ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }
        return false;
    }

    public boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void modeButton(DrawContext c, String label, int x, int y, int w, int h, int mx, int my, boolean selected) {
        boolean hover = inside(mx, my, x, y, w, h);
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, selected ? GuiTheme.alpha(GuiTheme.accent(), 80) : (hover ? 0xFF252A34 : 0xFF181C24), 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, selected ? GuiTheme.accent() : (hover ? 0xFF606575 : 0xFF292D36), 4);
        int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(label));
        text(c, label, x + (w - tw) / 2, y + (h - 8) / 2, selected ? 0xFFFFFFFF : 0xFFD4D8E0);
    }

    public void button(DrawContext c, String label, int x, int y, int w, int h, int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, h);
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, hover ? 0xFF252A34 : 0xFF181C24, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, hover ? GuiTheme.accent() : 0xFF292D36, 4);
        int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(label));
        text(c, label, x + (w - tw) / 2, y + (h - 8) / 2, 0xFFD4D8E0);
    }

    /**
     * Tasten-Knopf einer Modul-Karte. Doppelt belegte Tasten werden rot markiert, rechts daneben steht,
     * welches Modul die Taste schon hat. Beim Warten auf eine Taste steht dort der Hinweis zu Escape.
     */
    public void keybindButton(DrawContext c, String id, int key, boolean listening, int x, int y, int w, int h, int mx, int my) {
        String label = listening ? "..." : formatKey(key);
        String conflict = listening ? null : KeybindConflicts.conflictFor(id, key);
        int hintX = x + w + 6;
        // Karten beginnen 64 px links vom Knopf und sind eine Spalte breit
        int maxHintW = (x - 64 + getColumnWidth()) - hintX - 8;
        if (conflict == null) {
            button(c, label, x, y, w, h, mx, my);
            if (listening) drawHint(c, "Esc = none", hintX, y + (h - 8) / 2, maxHintW, 0xFF8E95A4);
            return;
        }
        boolean hover = inside(mx, my, x, y, w, h);
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, hover ? 0xFF3A1C22 : 0xFF2A161B, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, 0xFFEF4444, 4);
        int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(label));
        text(c, label, x + (w - tw) / 2, y + (h - 8) / 2, 0xFFFCA5A5);
        drawHint(c, "Also: " + conflict, hintX, y + (h - 8) / 2, maxHintW, 0xFFF87171);
    }

    private void drawHint(DrawContext c, String s, int x, int y, int maxW, int color) {
        if (maxW < 12) return;
        String t = s;
        if (textRenderer.getWidth(CustomGuiUtils.getFontText(t)) > maxW) {
            while (t.length() > 1 && textRenderer.getWidth(CustomGuiUtils.getFontText(t + "...")) > maxW) {
                t = t.substring(0, t.length() - 1);
            }
            t = t + "...";
        }
        text(c, t, x, y, color);
    }

    public void toggle(DrawContext c, int x, int y, boolean on, int mx, int my, float delta) {
        CustomToggleWidget.draw(c, x, y, 26, 14, on);
    }

    public void drawCustomSlider(DrawContext c, int sx, int sy, int sw, float normVal) {
        CustomGuiUtils.fillUltraRounded(c, sx, sy, sw, 4, 0xFF303442, 2);
        int fill = Math.round(sw * Math.clamp(normVal, 0.0f, 1.0f));
        if (fill > 0) {
            CustomGuiUtils.fillUltraRounded(c, sx, sy, fill, 4, GuiTheme.accent(), 2);
        }
        CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, sy - 2, 7, 8, 0xFFFFFFFF, 4);
    }

    public String formatKey(int key) {
        if (key < 0) return "None";
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) return "RSHIFT";
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT) return "LSHIFT";
        if (key == GLFW.GLFW_KEY_RIGHT_CONTROL) return "RCTRL";
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL) return "LCTRL";
        if (key == GLFW.GLFW_KEY_RIGHT_ALT) return "RALT";
        if (key == GLFW.GLFW_KEY_LEFT_ALT) return "LALT";
        String s = GLFW.glfwGetKeyName(key, 0);
        if (s != null) return s.toUpperCase(java.util.Locale.ROOT);
        return net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString().toUpperCase(java.util.Locale.ROOT);
    }

    public void openHudEditor() {
        if (client != null) {
            client.setScreen(new HudEditorScreen(this));
        }
    }

    public void openModalColorPicker(int type, int currentColor) {
        crosshairColorPickerOpen = (type == 0);
        hitColorColorPickerOpen = (type == 1);
        hitboxColorPickerOpen = (type == 2);
        blockOutlineColorPickerOpen = (type == 3);
        genericColorPickerOpen = false;
        if (type == 0) syncCrosshairHsv();
        else if (type == 1) syncHitColorHsv();
        else if (type == 2) syncHitboxHsv();
        else if (type == 3) syncBlockOutlineHsv();
    }

    /** Farbwähler für beliebige Einstellungen; jede Änderung geht sofort an {@code set} (Speichern macht der Dialog). */
    public void openColorPicker(String title, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
        openModalColorPicker(-1, 0);
        genericPickerTitle = title;
        genericPickerGet = get;
        genericPickerSet = set;
        genericColorPickerOpen = true;
        int color = get.getAsInt();
        float[] hsv = java.awt.Color.RGBtoHSB((color >> 16) & 255, (color >> 8) & 255, color & 255, null);
        cpHue = hsv[0];
        cpSat = hsv[1];
        cpVal = hsv[2];
    }

    private boolean anyColorPickerOpen() {
        return crosshairColorPickerOpen || hitColorColorPickerOpen || hitboxColorPickerOpen || blockOutlineColorPickerOpen
                || genericColorPickerOpen;
    }

    public int getColumnWidth() { return (cw - 16) / 2; }
    public int getContentLeftX() { return cx; }
    public int getContentRightX() { return cx + (cw - 16) / 2 + 16; }
    public int getCy() { return cy; }
    public int getCh() { return ch; }

    /**
     * Whether a card row from y to y + h overlaps the visible content area, so tabs can skip rendering cards that are
     * scrolled out of view (they still have to advance their ColumnTracker). Range cy - 2 .. cy + ch covers the content
     * scissor box of both the mods and the cosmetics view.
     */
    public boolean isVisibleRow(int y, int h) {
        return y + h > cy - 2 && y < cy + ch;
    }

    private static List<Item> getAllItems() {
        if (ALL_ITEMS == null) {
            ALL_ITEMS = Registries.ITEM.stream()
                    .filter(i -> i != Items.AIR)
                    .toList();
        }
        return ALL_ITEMS;
    }

    private static List<Item> getAllTools() {
        if (ALL_TOOLS == null) {
            ALL_TOOLS = Registries.ITEM.stream()
                    .filter(i -> i != Items.AIR && new ItemStack(i).isDamageable())
                    .toList();
        }
        return ALL_TOOLS;
    }

    private void updateItemFilter() {
        List<Item> all = (itemModalType == 1) ? getAllTools() : getAllItems();
        if (itemSearch == null || itemSearch.trim().isEmpty()) {
            cachedFilteredItems = all;
        } else {
            String q = itemSearch.trim().toLowerCase(java.util.Locale.ROOT);
            cachedFilteredItems = all.stream().filter(item -> {
                String name = item.getName().getString().toLowerCase(java.util.Locale.ROOT);
                if (name.contains(q)) return true;
                String id = Registries.ITEM.getId(item).toString().toLowerCase(java.util.Locale.ROOT);
                return id.contains(q);
            }).toList();
        }
        itemModalScroll = 0;
    }

    public void openItemModal() {
        itemModalType = 0;
        tempSelectedItems.clear();
        tempSelectedItems.addAll(ItemSizeModule.selectedItems);
        itemSearch = "";
        if (itemSearchWidget != null) {
            itemSearchWidget.setText("");
            itemSearchWidget.setFocused(true);
            setFocused(itemSearchWidget);
        }
        updateItemFilter();
        itemModalOpen = true;
    }

    public void openToolModal() {
        itemModalType = 1;
        tempToolThresholds.clear();
        tempToolThresholds.putAll(DurabilityGuardModule.toolThresholds);
        itemSearch = "";
        if (itemSearchWidget != null) {
            itemSearchWidget.setText("");
            itemSearchWidget.setFocused(true);
            setFocused(itemSearchWidget);
        }
        updateItemFilter();
        itemModalOpen = true;
    }

    private void applyModalSave() {
        if (itemModalType == 1) {
            DurabilityGuardModule.toolThresholds.clear();
            DurabilityGuardModule.toolThresholds.putAll(tempToolThresholds);
            toolThresholdFields.keySet().retainAll(DurabilityGuardModule.toolThresholds.keySet());
            BameClientConfig.save();
            layout();
        } else {
            ItemSizeModule.selectedItems.clear();
            ItemSizeModule.selectedItems.addAll(tempSelectedItems);
            BameClientConfig.save();
            layout();
        }
        itemModalOpen = false;
    }

    // Zeilen der Cosmetics-Seitenleiste: zwischen Überschrift und Zurück-Knopf (py + ph - 36), mit 8 px Abstand
    private java.util.List<CosmeticsTab.SidebarRow> cosmeticsSidebarRows() {
        return CosmeticsTab.sidebarRows(py + 48, py + ph - 36 - 8);
    }

    private void geometry() {
        if (currentView == ViewMode.COSMETICS) {
            pw = Math.min(width - 32, Math.max(760, (int) (width * 0.86)));
            ph = Math.min(height - 32, Math.max(460, (int) (height * 0.82)));
            px = (width - pw) / 2;
            py = (height - ph) / 2;
            sidebar = 112; // Dedicated wide category sidebar for cosmetics
            cx = px + sidebar + 12;
            cy = py + 42;
            cw = pw - sidebar - 24;
            ch = ph - 58; // Unterkante = Scissor-Unterkante (py + ph - 16), damit Klick- und Sichtbereich gleich sind
            if (search != null) {
                search.setX(cx + 4);
                search.setY(py + 9);
                search.setWidth(130);
                search.setHeight(18);
            }
        } else {
            pw = Math.min(width - 40, Math.max(660, (int) (width * 0.74)));
            ph = Math.min(height - 40, Math.max(400, (int) (height * 0.68)));
            px = (width - pw) / 2;
            py = (height - ph) / 2;
            sidebar = 68;
            cx = px + sidebar + 10;
            cy = py + 42;
            cw = pw - sidebar - 20;
            ch = ph - 62; // Unterkante = Scissor-Unterkante (py + ph - 20), damit Klick- und Sichtbereich gleich sind
            if (search != null) {
                search.setX(cx + cw - 128);
                search.setY(py + 9);
                search.setWidth(120);
                search.setHeight(20);
            }
        }
    }

    @Override
    protected void init() {
        com.bame.client.util.Prof.push("caeser_menu_init");
        try {
            initMenu();
            // Hintergrundbild schon jetzt im Hintergrund vorbereiten (der Hub zeigt es nicht, das Panel danach schon)
            com.bame.client.wallpaper.WallpaperManager.prefetch();
        } finally {
            com.bame.client.util.Prof.pop();
        }
    }

    private void initMenu() {
        if (openTime == 0) openTime = System.currentTimeMillis();
        truncatedDescriptions.clear();
        geometry();
        search = new CustomTextFieldWidget(cx + cw - 128, py + 8, 120, 18, Text.literal("Search"));
        search.setPlaceholder("Search...");
        search.setDrawsBackground(false);
        addSelectableChild(search);

        itemSearchWidget = new CustomTextFieldWidget(0, 0, 318, 20, Text.literal("Search"));
        itemSearchWidget.setPlaceholder("Search...");
        itemSearchWidget.setDrawsBackground(true);
        itemSearchWidget.setChangedListener(s -> {
            itemSearch = s;
            updateItemFilter();
        });
        addSelectableChild(itemSearchWidget);

        layout();
    }

    /**
     * Klick in Panel-Koordinaten. Bei aktivem 3D GUI wird mit der Inversen der zuletzt gezeichneten Panel-Matrix
     * umgerechnet (genau das, was zu sehen ist); sonst bleibt der Klick unverändert.
     */
    private Click toPanelClick(Click click) {
        if (!panel3D.isActive()) return click;
        double[] local = panel3D.toLocal(click.x(), click.y());
        return new Click(local[0], local[1], click.buttonInfo());
    }

    /** Ein Dialog liegt über dem Panel: Neigung und Schweben ausblenden, damit er gerade und ruhig steht. */
    private boolean panelModalOpen() {
        return itemModalOpen || crosshairColorPickerOpen || hitColorColorPickerOpen || hitboxColorPickerOpen
                || blockOutlineColorPickerOpen || genericColorPickerOpen
                || (currentView == ViewMode.COSMETICS && (CosmeticsTab.customSkinModalOpen || ShopOverlay.isModalOpen()));
    }

    private boolean leftMouseDown() {
        if (client == null || client.getWindow() == null) return false;
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private int columns() { return 4; }
    private int effectsY() { return 44 + ((GuiTheme.PRESETS.length + columns() - 1) / columns()) * 58 + 18; }
    private int settingsY() { return effectsY(); }

    private int contentHeight() {
        if (currentView == ViewMode.THEME) return settingsY() + themeSettings.height() + 8;
        if (currentView == ViewMode.SETTINGS) return settingsContentHeight;
        if (currentView == ViewMode.COSMETICS) {
            ClientTab cosTab = tabs.get(COSMETICS_TAB);
            if (cosTab != null) {
                ColumnTracker tracker = new ColumnTracker(0);
                cosTab.calculateHeight(this, getColumnWidth(), tracker, search != null ? search.getText().trim() : "");
                return Math.max(tracker.leftY, tracker.rightY);
            }
            return 400;
        }
        String q = (search != null) ? search.getText().trim() : "";
        ColumnTracker tracker = new ColumnTracker(0);
        for (ClientTab tab : modTabsForView(q)) {
            tab.calculateHeight(this, getColumnWidth(), tracker, q);
        }
        return Math.max(tracker.leftY, tracker.rightY);
    }

    /** Höhe der Einstellungs-Ansicht (wird beim Zeichnen ermittelt). */
    private int settingsContentHeight = 420;

    private double maxScroll() { return Math.max(0, contentHeight() - ch); }
    public double getScroll() { return scroll; }
    /** Aktueller Suchtext (getrimmt), leer ohne Suche. */
    public String getSearchQuery() { return search != null ? search.getText().trim() : ""; }
    public void setScroll(double s) { this.scroll = Math.clamp(s, 0, maxScroll()); }
    public double getMaxScroll() { return maxScroll(); }
    public int baseY() { return cy - (int) scroll; }

    public void layout() {
        scroll = Math.clamp(scroll, 0, maxScroll());
        int columnWidth = getColumnWidth();
        int leftX = getContentLeftX();
        int rightX = getContentRightX();
        String q = (search != null) ? search.getText().trim() : "";
        ColumnTracker tracker = new ColumnTracker(baseY());

        if (currentView == ViewMode.COSMETICS) {
            ClientTab cosTab = tabs.get(COSMETICS_TAB);
            if (cosTab != null) {
                cosTab.layout(this, leftX, rightX, columnWidth, tracker, q);
            }
            return;
        }

        if (currentView == ViewMode.THEME) {
            themeSettings.layout(cx, baseY() + settingsY(), cw);
            return;
        }
        if (currentView == ViewMode.SETTINGS) return;

        for (ClientTab tab : modTabsForView(q)) {
            tab.layout(this, leftX, rightX, columnWidth, tracker, q);
        }
    }

    private boolean isShiftDown() {
        if (client == null || client.getWindow() == null) return false;
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private boolean isSecretComboPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT ||
                key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL ||
                key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT) {
            return false;
        }
        if (key != BameClientConfig.secretKey) {
            return false;
        }
        int mods = input.modifiers();
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0 || isShiftDown();
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean alt = (mods & GLFW.GLFW_MOD_ALT) != 0;
        if (client != null && client.getWindow() != null) {
            long handle = client.getWindow().getHandle();
            if (!ctrl) ctrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            if (!alt) alt = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        }

        return shift == BameClientConfig.secretRequireShift
                && ctrl == BameClientConfig.secretRequireCtrl
                && alt == BameClientConfig.secretRequireAlt;
    }

    private String animName() {
        int a = BameClientConfig.guiAnimation;
        switch (a) {
            case 0: return "Fade In";
            case 1: return "Slide In";
            case 2: return "Elastic Bounce";
            case 3: return "Swirl Twist";
            default: return "None";
        }
    }

    private String keyNameMenuBind() {
        if (listeningMenuBind) return "...";
        return formatKey(BameClientConfig.menuBind);
    }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
        if (BameClientConfig.frostedBlur && !BameClientConfig.threeDGui) applyBlur(c);
    }

    private static final net.minecraft.util.Identifier HUB_LOGO =
        net.minecraft.util.Identifier.of("bameclient", "textures/font/caeser_badge.png");

    private void renderHub(DrawContext c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, 0x65000000);

        int cyCenter = height / 2;
        int cxCenter = width / 2;
        int accent = GuiTheme.accent();

        // Title: CAESER [C] CLIENT (matching Bild 1)
        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) cxCenter, (float) (cyCenter - 54));
        c.getMatrices().scale(1.85f, 1.85f);
        int wCaeser = textRenderer.getWidth(CustomGuiUtils.getFontText("CAESER"));
        int wLogo = 12;
        int wClient = textRenderer.getWidth(CustomGuiUtils.getFontText("CLIENT"));
        int totalLogoW = wCaeser + 6 + wLogo + 6 + wClient;
        int startX = -totalLogoW / 2;
        c.drawText(textRenderer, CustomGuiUtils.getFontText("CAESER"), startX, 0, 0xFFFFFFFF, false);
        // Gleiches Logo wie in der Tab-Liste, in der Theme-Farbe, vertikal mittig zur Großbuchstaben-Höhe der Schrift
        int logoY = (textRenderer.fontHeight - 1) / 2 - wLogo / 2;
        c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, HUB_LOGO, startX + wCaeser + 6, logoY,
            0.0f, 0.0f, wLogo, wLogo, 32, 32, 32, 32, 0xFF000000 | (accent & 0xFFFFFF));
        c.drawText(textRenderer, CustomGuiUtils.getFontText("CLIENT"), startX + wCaeser + 6 + wLogo + 6, 0, 0xFFFFFFFF, false);
        c.getMatrices().popMatrix();

        // Row of Quick Buttons (4 by default, 5 when secret key combo unlocked)
        int count = BameClientConfig.secretUnlocked ? 5 : 4;
        int iconSize = 34;
        int iconGap = 8;
        int totalRowW = count * iconSize + (count - 1) * iconGap;
        int rowStartX = cxCenter - totalRowW / 2;
        int rowY = cyCenter + 20;

        int hoveredQuick = -1;
        for (int i = 0; i < count; i++) {
            int bx = rowStartX + i * (iconSize + iconGap);
            if (inside(mx, my, bx, rowY, iconSize, iconSize)) {
                hoveredQuick = i;
                break;
            }
        }

        String mainBtnText = "MOD MENU";
        if (hoveredQuick == 0) mainBtnText = "GALLERY";
        else if (hoveredQuick == 1) mainBtnText = "COSMETICS";
        else if (hoveredQuick == 2) mainBtnText = "MOD MENU";
        else if (hoveredQuick == 3) mainBtnText = "HUD";
        else if (hoveredQuick == 4 && BameClientConfig.secretUnlocked) mainBtnText = "SECRET";

        // Main Button: [ MOD MENU ] (hover updates to hovered item, matching Bild 1/2)
        int btnW = 240;
        int btnH = 26;
        int btnX = cxCenter - btnW / 2;
        int btnY = cyCenter - 14;
        boolean btnHover = inside(mx, my, btnX, btnY, btnW, btnH);
        boolean activeHighlight = btnHover || (hoveredQuick >= 0);
        int btnBg = activeHighlight ? 0xFF242630 : 0xFF16171D;
        CustomGuiUtils.fillUltraRounded(c, btnX, btnY, btnW, btnH, btnBg, 4.0f);
        int btnBorder = activeHighlight ? accent : 0xFF262934;
        CustomGuiUtils.drawUltraRoundedOutline(c, btnX, btnY, btnW, btnH, btnBorder, 4.0f);
        int mmTextW = textRenderer.getWidth(CustomGuiUtils.getFontText(mainBtnText));
        c.drawText(textRenderer, CustomGuiUtils.getFontText(mainBtnText), btnX + (btnW - mmTextW) / 2, btnY + 8, activeHighlight ? accent : 0xFFE2E8F0, false);

        for (int i = 0; i < count; i++) {
            int bx = rowStartX + i * (iconSize + iconGap);
            boolean hover = (hoveredQuick == i);
            int bg = hover ? GuiTheme.alpha(accent, 38) : 0xFF16171D;
            CustomGuiUtils.fillUltraRounded(c, bx, rowY, iconSize, iconSize, bg, 4.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, bx, rowY, iconSize, iconSize, hover ? accent : 0xFF262934, 4.0f);

            int iconColor = hover ? accent : 0xFF9CA3AF;
            switch (i) {
                case 0 -> CustomGuiUtils.drawCameraIcon(c, bx + 8, rowY + 8, iconColor);
                case 1 -> CustomGuiUtils.drawShirtIcon(c, bx + 8, rowY + 8, iconColor);
                case 2 -> CustomGuiUtils.drawCLogo(c, bx + 8, rowY + 8, 18, hover ? accent : 0xFFFFFFFF);
                case 3 -> CustomGuiUtils.drawEmoteIcon(c, bx + 8, rowY + 8, iconColor);
                case 4 -> CustomGuiUtils.drawCrownIcon(c, bx + 8, rowY + 8, iconColor);
            }
        }

        com.bame.client.notification.NotificationManager.render(c, delta);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        if (!GuiProfiler.ENABLED) {
            com.bame.client.util.Prof.push("caeser_menu_render");
            try {
                renderScreen(c, mx, my, delta);
            } finally {
                com.bame.client.util.Prof.pop();
            }
            return;
        }
        long start = GuiProfiler.beginFrame();
        try {
            renderScreen(c, mx, my, delta);
        } finally {
            String view = currentView == ViewMode.MODS
                    ? "MODS/" + activeCategory() + (search != null && !search.getText().trim().isEmpty() ? " (search)" : "")
                    : currentView.name();
            GuiProfiler.endFrame(start, view);
        }
    }

    private void renderScreen(DrawContext c, int mx, int my, float delta) {
        // Jeton-Shop: Öffnen der Cosmetics-Ansicht erkennen (alle Wege: Hub, Seitenleiste, /cosmetics, Emote-Rad)
        if (currentView == ViewMode.COSMETICS) {
            if (lastRenderedView != ViewMode.COSMETICS) {
                ShopOverlay.onCosmeticsViewOpened();
            }
            ShopOverlay.tickOpen();
        } else if (lastRenderedView == ViewMode.COSMETICS) {
            ShopOverlay.close();
        }
        ViewMode previousView = lastRenderedView;
        lastRenderedView = currentView;

        if (currentView == ViewMode.HUB) {
            panel3D.deactivate();
            renderHub(c, mx, my, delta);
            return;
        }

        geometry();
        layout();
        float progress = Math.min(1.0f, (System.currentTimeMillis() - openTime) / 400.0f);
        float p = 1.0f - (float) Math.pow(1.0f - progress, 4);

        int dimAlpha = (int) (0x65 * p);
        if (dimAlpha > 0) {
            c.fill(0, 0, width, height, (dimAlpha << 24) | 0x000000);
        }

        // 3D GUI: Panel-Lage für diesen Frame berechnen, Tiefe dahinter zeichnen und die Transformation aufsetzen.
        // Ab hier sind mx/my Panel-Koordinaten (exakt mit der Inversen derselben Matrix umgerechnet).
        boolean threeD = BameClientConfig.threeDGui;
        if (threeD) {
            if (previousView == null || previousView == ViewMode.HUB || panelOpenNanos == 0) {
                panelOpenNanos = System.nanoTime(); // Panel fliegt beim Öffnen (auch aus dem Hub) herein
            }
            panel3D.frame(px, py, pw, ph, mx, my, panelOpenNanos, ThreeDGuiManager.motionEnabled(),
                    panelModalOpen(), leftMouseDown());
            float open = Panel3D.openProgress((System.nanoTime() - panelOpenNanos) / 1.0e9f, ThreeDGuiManager.motionEnabled());
            ThreeDGuiManager.renderBackDepth(c, panel3D, px, py, pw, ph, open);
            ThreeDGuiManager.pushPanel(c, panel3D);
            ThreeDGuiManager.renderPanelShadow(c, panel3D, px, py, pw, ph);
            double[] local = panel3D.toLocal(mx, my);
            mx = (int) Math.floor(local[0]);
            my = (int) Math.floor(local[1]);
        } else {
            panel3D.deactivate();
            panelOpenNanos = 0;
        }

        int bg = BameClientConfig.seeThrough ? 0xD810121A : 0xF710121A;
        CustomGuiUtils.fillUltraRounded(c, px, py, pw, ph, bg, 8.0f);

        if (BameClientConfig.customWallpaper) {
            if (threeD) {
                // Parallax: das Hintergrundbild bewegt sich minimal gegenläufig zur Neigung
                com.bame.client.wallpaper.WallpaperManager.render(c, px, py, pw, ph, p, true, panel3D.tiltX(), panel3D.tiltY());
            } else {
                com.bame.client.wallpaper.WallpaperManager.render(c, px, py, pw, ph, p);
            }
        }

        CustomGuiUtils.drawUltraRoundedOutline(c, px, py, pw, ph, 0xFF242834, 8.0f);

        // Left Vertical Sidebar
        c.fill(px + sidebar - 1, py, px + sidebar, py + ph, 0xFF232530);
        int accent = GuiTheme.accent();

        if (currentView == ViewMode.COSMETICS) {
            // Dedicated Clean Cosmetics Sidebar (Matching NoRisk Client Structure)
            CustomGuiUtils.drawShirtIcon(c, px + 12, py + 14, accent);
            c.drawText(textRenderer, CustomGuiUtils.getFontText("COSMETICS"), px + 34, py + 18, 0xFFFFFFFF, false);

            int catBtnW = sidebar - 16;
            for (CosmeticsTab.SidebarRow row : cosmeticsSidebarRows()) {
                CosmeticsTab.SidebarEntry entry = row.entry();
                int by = row.y();
                int bh = row.height();
                int textY = by + (bh - 8) / 2;
                boolean hover = inside(mx, my, px + 8, by, catBtnW, bh);

                if (entry.isHeader()) {
                    // Dropdown-Kopf (COSMETICS / EMOTES): aufgeklappt mit Akzentbalken und Akzentfarbe
                    boolean open = CosmeticsTab.isExpanded(entry.id());
                    if (open) c.fill(px + 8, by + 2, px + 10, by + bh - 2, accent);
                    int col = open ? accent : (hover ? 0xFFFFFFFF : 0xFFE2E8F0);
                    c.drawText(textRenderer, CustomGuiUtils.getFontText(entry.label()), px + 14, textY, col, false);
                } else {
                    // Unterkategorie: ▪ grau, aktuelle (Scroll-Spy der offenen Ansicht) mit ▸ und Akzentfarbe
                    boolean active = CosmeticsTab.isSidebarEntryActive(entry);
                    int markerCol = active ? accent : 0xFF6B7280;
                    int col = active ? accent : (hover ? 0xFFFFFFFF : 0xFFCBD5E1);
                    c.drawText(textRenderer, CustomGuiUtils.getFontText(active ? "▸" : "▪"), px + 20, textY, markerCol, false);
                    c.drawText(textRenderer, CustomGuiUtils.getFontText(entry.label()), px + 30, textY, col, false);
                }
            }

            // Bottom: [← Back]
            int backBtnH = 26;
            int backBtnY = py + ph - 36;
            boolean hoverBack = inside(mx, my, px + 8, backBtnY, catBtnW, backBtnH);
            CustomGuiUtils.fillUltraRounded(c, px + 8, backBtnY, catBtnW, backBtnH, hoverBack ? 0xFF242838 : 0xFF161822, 4.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, px + 8, backBtnY, catBtnW, backBtnH, hoverBack ? accent : 0xFF2A2F40, 4.0f);
            c.drawText(textRenderer, CustomGuiUtils.getFontText("← Back"), px + 18, backBtnY + 8, hoverBack ? 0xFFFFFFFF : 0xFF9CA3AF, false);
        } else {
            // Linke Leiste: MODS, COSMETICS, SKINS, GALLERY, SETTINGS (Höhe passt sich kleinen Fenstern an), unten HUB
            for (int i = 0; i < SIDEBAR_ENTRIES; i++) {
                renderSidebarEntry(c, i, mx, my, accent);
            }

            // Bottom: ⌂ HUB
            int sbBtnX = px + 5;
            int hubBtnH = 32;
            int hubBtnY = py + ph - 42;
            boolean hoverHub = inside(mx, my, sbBtnX, hubBtnY, SIDEBAR_BTN_W, hubBtnH);
            if (hoverHub) {
                CustomGuiUtils.fillUltraRounded(c, sbBtnX, hubBtnY, SIDEBAR_BTN_W, hubBtnH, 0xFF20232E, 4.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, sbBtnX, hubBtnY, SIDEBAR_BTN_W, hubBtnH, 0xFF3A3F50, 4.0f);
            }
            int twHub = textRenderer.getWidth(CustomGuiUtils.getFontText("HUB"));
            c.drawText(textRenderer, CustomGuiUtils.getFontText("HUB"), sbBtnX + (SIDEBAR_BTN_W - twHub) / 2, hubBtnY + 12, hoverHub ? 0xFFFFFFFF : 0xFF8E95A4, false);
        }

        // Content Area rendering by ViewMode:
        if (currentView == ViewMode.MODS) {
            // Top Category Tabs Header (Combat | Movement | Visuals | World | Misc) – passt sich der Breite an
            HeaderLayout hl = modsHeaderLayout();
            int tabH = 18;
            int tabY = py + 9;
            for (int i = 0; i < CATEGORIES.length; i++) {
                int tabX = hl.tabX()[i];
                int tabW = hl.tabW()[i];
                String label = hl.labels()[i];
                int lw = textRenderer.getWidth(CustomGuiUtils.getFontText(label));
                int textX = tabX + (tabW - lw) / 2;
                boolean active = activeCategory().equalsIgnoreCase(CATEGORIES[i]);
                boolean hover = inside(mx, my, tabX, tabY, tabW, tabH);
                if (active) {
                    CustomGuiUtils.fillUltraRounded(c, tabX, tabY, tabW, tabH, GuiTheme.alpha(accent, 38), 3.0f);
                    CustomGuiUtils.drawUltraRoundedOutline(c, tabX, tabY, tabW, tabH, accent, 3.0f);
                    text(c, label, textX, tabY + 5, accent);
                } else {
                    if (hover) {
                        CustomGuiUtils.fillUltraRounded(c, tabX, tabY, tabW, tabH, 0xFF1C1E28, 3.0f);
                    }
                    text(c, label, textX, tabY + 5, hover ? 0xFFFFFFFF : 0xFF8E95A4);
                }
            }

            // Top Search Widget
            int searchBoxX = hl.searchX();
            int searchBoxW = hl.searchW();
            int searchBoxY = py + 8;
            CustomGuiUtils.fillUltraRounded(c, searchBoxX, searchBoxY, searchBoxW, 18, 0xFF16171E, 3.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, searchBoxX, searchBoxY, searchBoxW, 18, 0xFF292C37, 3.0f);
            CustomGuiUtils.drawSearchIcon(c, searchBoxX + 6, searchBoxY + 5, 0xFF8E95A4);
            search.setX(searchBoxX + 18);
            search.setY(searchBoxY + 1);
            search.setWidth(Math.max(10, searchBoxW - 22));
            search.setHeight(16);
            search.render(c, mx, my, delta);

            // Divider line below top bar
            c.fill(cx, py + 34, cx + cw, py + 35, 0xFF222530);

            // Modules Cards content (Scissor = Klickbereich cx..cx+cw, cy..cy+ch)
            c.enableScissor(cx - 4, cy - 2, cx + cw + 4, cy + ch);

            int columnWidth = getColumnWidth();
            int leftX = getContentLeftX();
            int rightX = getContentRightX();
            String q = search.getText().trim();
            ColumnTracker tracker = new ColumnTracker(baseY());
            for (ClientTab tab : modTabsForView(q)) {
                tab.render(this, c, mx, my, delta, leftX, rightX, columnWidth, tracker, q);
            }
            c.disableScissor();

            // Scrollbar
            if (maxScroll() > 0) {
                int sbX = cx + cw + 2;
                box(c, sbX, cy, 3, ch - 8, 0xFF232631);
                box(c, sbX, thumbY(), 3, thumbHeight(), scrollDragging ? 0xFFFFFFFF : accent);
            }

        } else if (currentView == ViewMode.COSMETICS) {
            // Top Bar: Search Input (Left) + Category indicator (Middle) + Unequip All Button (Right)
            int searchBoxX = cx;
            int searchBoxY = py + 8;
            int searchBoxW = Math.clamp(cw / 3, 80, 160);
            int searchBoxH = 20;
            CustomGuiUtils.fillUltraRounded(c, searchBoxX, searchBoxY, searchBoxW, searchBoxH, 0xFF141722, 3.5f);
            CustomGuiUtils.drawUltraRoundedOutline(c, searchBoxX, searchBoxY, searchBoxW, searchBoxH, 0xFF282F40, 3.5f);
            CustomGuiUtils.drawSearchIcon(c, searchBoxX + 6, searchBoxY + 6, 0xFF8E95A4);
            search.setX(searchBoxX + 20);
            search.setY(searchBoxY + 1);
            search.setWidth(searchBoxW - 26);
            search.setHeight(18);
            search.render(c, mx, my, delta);

            // Top-Right: Quick Unequip All Button
            int unequipW = 110;
            int unequipH = 20;
            int unequipX = cx + cw - unequipW;
            int unequipY = py + 8;

            // Active Category Label (nur wenn Platz zwischen Suche und Jeton-Anzeige/Unequip ist)
            String catTitle = CosmeticsTab.currentViewTitle();
            int titleX = searchBoxX + searchBoxW + 16;
            if (titleX + textRenderer.getWidth(CustomGuiUtils.getFontText(catTitle)) < unequipX - 90) {
                text(c, catTitle, titleX, py + 14, 0xFF94A3B8);
            }

            boolean hoverUnequip = inside(mx, my, unequipX, unequipY, unequipW, unequipH);
            CustomGuiUtils.fillUltraRounded(c, unequipX, unequipY, unequipW, unequipH, hoverUnequip ? 0xFF351C24 : 0xFF17151D, 4.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, unequipX, unequipY, unequipW, unequipH, hoverUnequip ? 0xFFF43F5E : 0xFF2D2430, 4.0f);
            int twUnequip = textRenderer.getWidth(CustomGuiUtils.getFontText("✕ Unequip All"));
            text(c, "✕ Unequip All", unequipX + (unequipW - twUnequip) / 2, unequipY + 6, hoverUnequip ? 0xFFFCA5A5 : 0xFFCBD5E1);

            // Jeton-Kontostand links neben "Unequip All"
            ShopOverlay.renderChip(this, c, unequipX - 8, unequipY, unequipH, mx, my);

            // Divider line below top bar
            c.fill(cx, py + 34, cx + cw, py + 35, 0xFF222530);

            // Content Area (Cards + Preview), Scissor = Klickbereich
            c.enableScissor(cx - 4, cy - 2, cx + cw + 4, cy + ch);
            int columnWidth = getColumnWidth();
            int leftX = getContentLeftX();
            int rightX = getContentRightX();
            String q = search.getText().trim();
            ColumnTracker tracker = new ColumnTracker(baseY());
            ClientTab cosTab = tabs.get(COSMETICS_TAB);
            if (cosTab != null) {
                cosTab.render(this, c, mx, my, delta, leftX, rightX, columnWidth, tracker, q);
            }
            c.disableScissor();

            // Scrollbar
            if (maxScroll() > 0) {
                int sbX = cx + cw + 2;
                box(c, sbX, cy, 3, ch - 8, 0xFF232631);
                box(c, sbX, thumbY(), 3, thumbHeight(), scrollDragging ? 0xFFFFFFFF : accent);
            }

        } else if (currentView == ViewMode.THEME || currentView == ViewMode.SETTINGS) {
            // Kopfzeile der Einstellungen: GENERAL | APPEARANCE
            renderSettingsHeader(c, mx, my, accent);
            c.fill(cx, py + 34, cx + cw, py + 35, 0xFF222530);

            c.enableScissor(cx - 4, cy - 2, cx + cw + 4, cy + ch);
            if (currentView == ViewMode.THEME) {
                renderTheme(c, mx, my, delta);
            } else {
                renderSettings(c, mx, my, delta);
            }
            c.disableScissor();

            if (maxScroll() > 0) {
                int sbX = cx + cw + 2;
                box(c, sbX, cy, 3, ch - 8, 0xFF232631);
                box(c, sbX, thumbY(), 3, thumbHeight(), scrollDragging ? 0xFFFFFFFF : accent);
            }
        }

        if (threeD) {
            // Glas-Kante: Rim-Light in der Akzentfarbe und Glanz an der Oberkante (über dem Inhalt, unter Dialogen)
            ThreeDGuiManager.renderPanelRim(c, panel3D, px, py, pw, ph);
        }

        if (anyColorPickerOpen()) {
            renderModalColorPicker(c, mx, my);
        }

        if (itemModalOpen) {
            renderItemModal(c, mx, my, delta);
        }

        if (currentView == ViewMode.COSMETICS && CosmeticsTab.customSkinModalOpen) {
            CosmeticsTab.renderCustomSkinModal(this, c, mx, my, delta);
        }

        if (currentView == ViewMode.COSMETICS) {
            ShopOverlay.renderOverlay(this, c, mx, my);
        }

        if (threeD) ThreeDGuiManager.popPanel(c, panel3D);

        com.bame.client.notification.NotificationManager.render(c, delta);
    }

    // ------------------------------------------------------------------------------------------------
    // Linke Leiste (MODS, COSMETICS, SKINS, GALLERY, SETTINGS)
    // ------------------------------------------------------------------------------------------------

    private static final int SIDEBAR_ENTRIES = 5;
    private static final int SIDEBAR_BTN_W = 58;
    private static final int SIDEBAR_GAP = 6;

    /** Knopfhöhe der linken Leiste: 46 px, bei kleinen Fenstern so klein, dass alles über HUB passt. */
    private int sidebarBtnH() {
        int avail = ph - 10 - 48; // oben 10 px, unten Platz für HUB (py + ph - 42) plus Abstand
        int h = (avail - (SIDEBAR_ENTRIES - 1) * SIDEBAR_GAP) / SIDEBAR_ENTRIES;
        return Math.clamp(h, 20, 46);
    }

    private int sidebarBtnY(int i) {
        return py + 10 + i * (sidebarBtnH() + SIDEBAR_GAP);
    }

    private boolean sidebarEntryActive(int i) {
        return switch (i) {
            case 0 -> currentView == ViewMode.MODS;
            case 1 -> currentView == ViewMode.COSMETICS && !"SKINS".equalsIgnoreCase(CosmeticsTab.subCategory);
            case 2 -> currentView == ViewMode.COSMETICS && "SKINS".equalsIgnoreCase(CosmeticsTab.subCategory);
            case 4 -> currentView == ViewMode.SETTINGS || currentView == ViewMode.THEME;
            default -> false;
        };
    }

    private void renderSidebarEntry(DrawContext c, int i, int mx, int my, int accent) {
        int x = px + 5;
        int y = sidebarBtnY(i);
        int h = sidebarBtnH();
        boolean active = sidebarEntryActive(i);
        boolean hover = inside(mx, my, x, y, SIDEBAR_BTN_W, h);
        if (active || hover) {
            CustomGuiUtils.fillUltraRounded(c, x, y, SIDEBAR_BTN_W, h, active ? GuiTheme.alpha(accent, 38) : 0xFF20232E, 4.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, x, y, SIDEBAR_BTN_W, h, active ? accent : 0xFF3A3F50, 4.0f);
        }
        boolean withLabel = h >= 38;
        int iconY = withLabel ? y + 4 : y + (h - 18) / 2;
        int iconCol = active ? accent : (hover ? 0xFFFFFFFF : 0xFF9CA3AF);
        String label;
        switch (i) {
            case 0 -> { CustomGuiUtils.drawCLogo(c, x + 20, iconY, 18, iconCol); label = "MODS"; }
            case 1 -> { CustomGuiUtils.drawShirtIcon(c, x + 20, iconY, iconCol); label = "COSMETICS"; }
            case 2 -> { CustomGuiUtils.drawSkinIcon(c, x + 20, iconY, iconCol); label = "SKINS"; }
            case 3 -> { CustomGuiUtils.drawCameraIcon(c, x + 20, iconY, iconCol); label = "GALLERY"; }
            default -> { CustomGuiUtils.drawGearIcon(c, x + 20, iconY, iconCol); label = "SETTINGS"; }
        }
        if (withLabel) {
            int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(label));
            c.drawText(textRenderer, CustomGuiUtils.getFontText(label), x + (SIDEBAR_BTN_W - tw) / 2, y + 28,
                    active ? accent : (hover ? 0xFFFFFFFF : 0xFF8E95A4), false);
        }
    }

    /** Klick auf einen Eintrag der linken Leiste oder HUB; true wenn behandelt. */
    private boolean clickSidebar(double mx, double my) {
        int x = px + 5;
        for (int i = 0; i < SIDEBAR_ENTRIES; i++) {
            if (!inside(mx, my, x, sidebarBtnY(i), SIDEBAR_BTN_W, sidebarBtnH())) continue;
            switch (i) {
                case 0 -> {
                    if (currentView != ViewMode.MODS) switchView(ViewMode.MODS);
                }
                case 1 -> {
                    if (currentView != ViewMode.COSMETICS || "SKINS".equalsIgnoreCase(CosmeticsTab.subCategory)) {
                        CosmeticsTab.subCategory = "ALL";
                        switchView(ViewMode.COSMETICS);
                    }
                }
                case 2 -> {
                    if (currentView != ViewMode.COSMETICS || !"SKINS".equalsIgnoreCase(CosmeticsTab.subCategory)) {
                        CosmeticsTab.subCategory = "SKINS";
                        switchView(ViewMode.COSMETICS);
                    }
                }
                case 3 -> {
                    com.bame.client.sound.ClientSoundManager.playClick();
                    client.setScreen(new GalleryScreen(this));
                }
                default -> {
                    if (currentView != ViewMode.SETTINGS && currentView != ViewMode.THEME) switchView(ViewMode.SETTINGS);
                }
            }
            return true;
        }
        if (inside(mx, my, x, py + ph - 42, SIDEBAR_BTN_W, 34)) {
            switchView(ViewMode.HUB);
            return true;
        }
        return false;
    }

    private void switchView(ViewMode view) {
        currentView = view;
        scroll = 0;
        listeningMenuBind = false;
        cancelAllKeyListening();
        themeSettings.close();
        com.bame.client.sound.ClientSoundManager.playClick();
        geometry();
        layout();
    }

    // ------------------------------------------------------------------------------------------------
    // Kopfzeile der Mod-Ansicht: Kategorie-Tabs links, Suchfeld rechts, ohne Überlappung
    // ------------------------------------------------------------------------------------------------

    private record HeaderLayout(int[] tabX, int[] tabW, String[] labels, int searchX, int searchW) {}

    private static final String[] SHORT_CATEGORY_LABELS = {"CMB", "MOVE", "VIS", "WRLD", "MISC"};

    private HeaderLayout modsHeaderLayout() {
        int startX = cx + 4;
        int right = cx + cw - 2;            // rechte Kante des Suchfelds
        int searchW = 126;
        String[] labels = new String[CATEGORIES.length];
        for (int i = 0; i < CATEGORIES.length; i++) labels[i] = CATEGORIES[i].toUpperCase(java.util.Locale.ROOT);
        int pad = 16, gap = 8;
        int total = headerTabsWidth(labels, pad, gap);
        if (startX + total + 8 + searchW > right) {
            searchW = Math.max(70, right - startX - total - 8);
        }
        if (startX + total + 8 + searchW > right) {
            pad = 8;
            gap = 3;
            total = headerTabsWidth(labels, pad, gap);
        }
        if (startX + total + 8 + searchW > right) {
            labels = SHORT_CATEGORY_LABELS.clone();
            total = headerTabsWidth(labels, pad, gap);
            searchW = Math.max(40, Math.min(126, right - startX - total - 8));
        }
        int[] tabX = new int[labels.length];
        int[] tabW = new int[labels.length];
        int x = startX;
        for (int i = 0; i < labels.length; i++) {
            tabX[i] = x;
            tabW[i] = textRenderer.getWidth(CustomGuiUtils.getFontText(labels[i])) + pad;
            x += tabW[i] + gap;
        }
        return new HeaderLayout(tabX, tabW, labels, right - searchW, searchW);
    }

    private int headerTabsWidth(String[] labels, int pad, int gap) {
        int total = 0;
        for (String l : labels) total += textRenderer.getWidth(CustomGuiUtils.getFontText(l)) + pad;
        return total + gap * (labels.length - 1);
    }

    // ------------------------------------------------------------------------------------------------
    // Einstellungen: Kopfzeile GENERAL | APPEARANCE
    // ------------------------------------------------------------------------------------------------

    private static final String[] SETTINGS_TABS = {"GENERAL", "APPEARANCE"};

    private int settingsTabX(int i) {
        int x = cx + 4;
        for (int k = 0; k < i; k++) x += settingsTabW(k) + 8;
        return x;
    }

    private int settingsTabW(int i) {
        return textRenderer.getWidth(CustomGuiUtils.getFontText(SETTINGS_TABS[i])) + 16;
    }

    private void renderSettingsHeader(DrawContext c, int mx, int my, int accent) {
        int tabY = py + 9;
        for (int i = 0; i < SETTINGS_TABS.length; i++) {
            int tabX = settingsTabX(i);
            int tabW = settingsTabW(i);
            boolean active = (i == 0) == (currentView == ViewMode.SETTINGS);
            boolean hover = inside(mx, my, tabX, tabY, tabW, 18);
            if (active) {
                CustomGuiUtils.fillUltraRounded(c, tabX, tabY, tabW, 18, GuiTheme.alpha(accent, 38), 3.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, tabX, tabY, tabW, 18, accent, 3.0f);
            } else if (hover) {
                CustomGuiUtils.fillUltraRounded(c, tabX, tabY, tabW, 18, 0xFF1C1E28, 3.0f);
            }
            text(c, SETTINGS_TABS[i], tabX + 8, tabY + 5, active ? accent : (hover ? 0xFFFFFFFF : 0xFF8E95A4));
        }
    }

    private boolean clickSettingsHeader(double mx, double my) {
        for (int i = 0; i < SETTINGS_TABS.length; i++) {
            if (inside(mx, my, settingsTabX(i), py + 9, settingsTabW(i), 18)) {
                ViewMode target = i == 0 ? ViewMode.SETTINGS : ViewMode.THEME;
                if (currentView != target) switchView(target);
                return true;
            }
        }
        return false;
    }

    private void syncHitboxHsv() {
        float[] hsv = java.awt.Color.RGBtoHSB((CustomHitboxesModule.color >> 16) & 255, (CustomHitboxesModule.color >> 8) & 255, CustomHitboxesModule.color & 255, null);
        cpHue = hsv[0];
        cpSat = hsv[1];
        cpVal = hsv[2];
    }

    private void syncBlockOutlineHsv() {
        float[] hsv = java.awt.Color.RGBtoHSB((BlockOutlineModule.color >> 16) & 255, (BlockOutlineModule.color >> 8) & 255, BlockOutlineModule.color & 255, null);
        cpHue = hsv[0];
        cpSat = hsv[1];
        cpVal = hsv[2];
    }

    private void syncCrosshairHsv() {
        float[] hsv = java.awt.Color.RGBtoHSB((CustomCrosshairModule.color >> 16) & 255, (CustomCrosshairModule.color >> 8) & 255, CustomCrosshairModule.color & 255, null);
        cpHue = hsv[0];
        cpSat = hsv[1];
        cpVal = hsv[2];
    }

    private void syncHitColorHsv() {
        float[] hsv = java.awt.Color.RGBtoHSB((HitColorModule.color >> 16) & 255, (HitColorModule.color >> 8) & 255, HitColorModule.color & 255, null);
        cpHue = hsv[0];
        cpSat = hsv[1];
        cpVal = hsv[2];
    }

    private void updateModalColor(double mx, double my) {
        int CP_W = 148;
        int CP_H = 76;
        int myCpX = (width - CP_W) / 2;
        int myCpY = (height - CP_H) / 2;
        int svX = myCpX + 10;
        int svY = myCpY + 22;
        int svW = 92;
        int svH = 30;
        int barX = myCpX + 10;
        int barW = 128;

        if (cpDrag == 0) {
            cpSat = (float) Math.clamp((mx - svX) / (double) svW, 0.0, 1.0);
            cpVal = 1.0f - (float) Math.clamp((my - svY) / (double) svH, 0.0, 1.0);
        } else if (cpDrag == 1) {
            cpHue = (float) Math.clamp((mx - barX) / (double) barW, 0.0, 1.0);
        }
        int rgb = java.awt.Color.HSBtoRGB(cpHue, cpSat, cpVal) & 0xFFFFFF;
        if (genericColorPickerOpen) {
            genericPickerSet.accept(0xFF000000 | rgb);
        } else if (hitColorColorPickerOpen) {
            HitColorModule.color = 0xFF000000 | rgb;
            HitColorModule.apply();
        } else if (hitboxColorPickerOpen) {
            CustomHitboxesModule.color = 0xFF000000 | rgb;
        } else if (blockOutlineColorPickerOpen) {
            BlockOutlineModule.color = 0xFF000000 | rgb;
        } else {
            CustomCrosshairModule.color = 0xFF000000 | rgb;
        }
        BameClientConfig.save();
    }

    private void renderModalColorPicker(DrawContext context, int mouseX, int mouseY) {
        int CP_W = 148;
        int CP_H = 76;
        int myCpX = (width - CP_W) / 2;
        int myCpY = (height - CP_H) / 2;

        context.fill(myCpX, myCpY, myCpX + CP_W, myCpY + CP_H, 0xF013171F);
        CustomGuiUtils.drawUltraRoundedOutline(context, myCpX, myCpY, CP_W, CP_H, 0xFF353C4D, 4);

        String title = genericColorPickerOpen ? genericPickerTitle : hitColorColorPickerOpen ? "HIT COLOR" : (hitboxColorPickerOpen ? "HITBOX COLOR" : (blockOutlineColorPickerOpen ? "BLOCK OUTLINE COLOR" : "COLOR PICKER"));
        text(context, title, myCpX + 10, myCpY + 8, 0xFF8E95A4);

        int curCol = genericColorPickerOpen ? genericPickerGet.getAsInt() : hitColorColorPickerOpen ? HitColorModule.color : (hitboxColorPickerOpen ? CustomHitboxesModule.color : (blockOutlineColorPickerOpen ? BlockOutlineModule.color : CustomCrosshairModule.color));
        int swatchX = myCpX + CP_W - 36;
        int swatchY = myCpY + 22;
        int swatchSize = 26;
        context.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, curCol);
        CustomGuiUtils.drawUltraRoundedOutline(context, swatchX, swatchY, swatchSize, swatchSize, 0xFF4A5568, 3);

        int svX = myCpX + 10;
        int svY = myCpY + 22;
        int svW = 92;
        int svH = 30;

        for (int px = 0; px < svW; px++) {
            float s = px / (float) (svW - 1);
            int colTop = java.awt.Color.HSBtoRGB(cpHue, s, 1.0f);
            context.fillGradient(svX + px, svY, svX + px + 1, svY + svH, colTop, 0xFF000000);
        }
        CustomGuiUtils.drawUltraRoundedOutline(context, svX - 1, svY - 1, svW + 2, svH + 2, 0xFF353C4D, 2);

        int hx = svX + (int) (cpSat * (svW - 1));
        int hy = svY + (int) ((1.0f - cpVal) * (svH - 1));
        context.fill(hx - 2, hy - 2, hx + 3, hy + 3, 0xFF000000);
        context.fill(hx - 1, hy - 1, hx + 2, hy + 2, 0xFFFFFFFF);

        int barX = myCpX + 10;
        int barW = 128;
        int hueY = myCpY + 58;
        int barH = 8;

        for (int px = 0; px < barW; px++) {
            float h = px / (float) (barW - 1);
            int col = java.awt.Color.HSBtoRGB(h, 1.0f, 1.0f);
            context.fill(barX + px, hueY, barX + px + 1, hueY + barH, col);
        }
        CustomGuiUtils.drawUltraRoundedOutline(context, barX - 1, hueY - 1, barW + 2, barH + 2, 0xFF353C4D, 2);

        int hueHandleX = barX + (int) (cpHue * (barW - 1));
        context.fill(hueHandleX - 1, hueY - 2, hueHandleX + 2, hueY + barH + 2, 0xFF000000);
        context.fill(hueHandleX, hueY - 1, hueHandleX + 1, hueY + barH + 1, 0xFFFFFFFF);
    }

    private void renderTheme(DrawContext c, int mx, int my, float delta) {
        int y = baseY(), cols = columns(), tileW = (cw - 8 * (cols - 1)) / cols;
        text(c, "PRESETS", cx + 4, y + 4, 0xFFE2E5ED);
        text(c, "Choose a palette for your client", cx + 4, y + 23, 0xFF8E95A4);
        for (int i = 0; i < GuiTheme.PRESETS.length; i++) {
            var p = GuiTheme.PRESETS[i];
            int x = cx + (i % cols) * (tileW + 8), ty = y + 44 + (i / cols) * 58;
            box(c, x, ty, tileW, 50, 0xFF151A21);
            if (!BameClientConfig.customMainColor && p.name().equals(GuiTheme.current().name()))
                CustomGuiUtils.drawUltraRoundedOutline(c, x, ty, tileW, 50, GuiTheme.accent(), 6);
            c.enableScissor(x + 4, ty, x + tileW - 4, ty + 23);
            text(c, p.name(), x + 7, ty + 7, 0xFFCCD2DD);
            c.disableScissor();
            int[] colors = {p.primary(), p.secondary(), p.highlight(), p.background()};
            for (int j = 0; j < 4; j++) c.fill(x + 6 + j * (tileW - 12) / 4, ty + 23, x + 6 + (j + 1) * (tileW - 12) / 4, ty + 43, 0xFF000000 | colors[j]);
        }
        themeSettings.render(c, mx, my, delta);
    }

    private void renderSettingCard(DrawContext c, int x, int y, int w, int h, String title, String subtitle) {
        renderSettingCard(c, x, y, w, h, title, subtitle, 0xFF7E8496);
    }

    private void renderSettingCard(DrawContext c, int x, int y, int w, int h, String title, String subtitle, int subtitleColor) {
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, 0xE8131620, 6.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, 0xFF222634, 6.0f);
        // Rechts sitzt das Bedienelement (max. ~90 px): Texte vorher kürzen, damit nichts überlappt
        drawHint(c, title, x + 12, y + 8, w - 12 - 96, 0xFFEEEEF2);
        drawHint(c, subtitle, x + 12, y + 22, w - 12 - 96, subtitleColor);
    }

    private void renderSettings(DrawContext c, int mx, int my, float delta) {
        int colW = (cw - 12) / 2;
        int col1X = cx;
        int col2X = cx + colW + 12;
        int cardH = 38;
        int cardGap = 8;
        int startY = baseY();
        int curY = startY + 4;

        // Section: INTERFACE
        text(c, "INTERFACE", cx + 2, curY + 2, 0xFF7E8496);
        c.fill(cx + 62, curY + 6, cx + cw, curY + 7, 0xFF202430);
        curY += 16;

        // Row 1: Menu Key | 3D GUI
        String menuConflict = listeningMenuBind ? null : KeybindConflicts.conflictFor(KeybindConflicts.MENU_ID, BameClientConfig.menuBind);
        String menuSub = listeningMenuBind ? "Press a key - Esc: cancel - Del: remove"
                : (menuConflict != null ? "Also used by " + menuConflict : "Key that opens this menu");
        renderSettingCard(c, col1X, curY, colW, cardH, "Menu Key", menuSub,
                listeningMenuBind ? GuiTheme.accent() : (menuConflict != null ? 0xFFF87171 : 0xFF7E8496));
        int mbX = col1X + colW - 68, mbY = curY + 10;
        if (menuConflict != null) {
            boolean hover = inside(mx, my, mbX, mbY, 58, 18);
            CustomGuiUtils.fillUltraRounded(c, mbX, mbY, 58, 18, hover ? 0xFF3A1C22 : 0xFF2A161B, 4);
            CustomGuiUtils.drawUltraRoundedOutline(c, mbX, mbY, 58, 18, 0xFFEF4444, 4);
            String l = keyNameMenuBind();
            int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(l));
            text(c, l, mbX + (58 - tw) / 2, mbY + 5, 0xFFFCA5A5);
        } else {
            button(c, keyNameMenuBind(), mbX, mbY, 58, 18, mx, my);
        }

        renderSettingCard(c, col2X, curY, colW, cardH, "3D GUI", "Floating panel in the world");
        toggle(c, col2X + colW - 36, curY + 12, BameClientConfig.threeDGui, mx, my, delta);
        curY += cardH + cardGap;

        // Row 2: GUI Animation | Toast Notifications
        renderSettingCard(c, col1X, curY, colW, cardH, "GUI Animation", "Smooth window open animations");
        button(c, animName(), col1X + colW - 88, curY + 10, 78, 18, mx, my);

        renderSettingCard(c, col2X, curY, colW, cardH, "Notifications", "Toasts when modules toggle");
        toggle(c, col2X + colW - 36, curY + 12, BameClientConfig.notifications, mx, my, delta);
        curY += cardH + 16;

        // Section: SOUNDS
        text(c, "SOUNDS", cx + 2, curY + 2, 0xFF7E8496);
        c.fill(cx + 48, curY + 6, cx + cw, curY + 7, 0xFF202430);
        curY += 16;

        // Row 1: Module Sound | Hover Sound
        renderSettingCard(c, col1X, curY, colW, cardH, "Module Sound", "Play on every module toggle");
        toggle(c, col1X + colW - 36, curY + 12, BameClientConfig.moduleSound, mx, my, delta);

        renderSettingCard(c, col2X, curY, colW, cardH, "Hover Sound", "Soft tick when hovering menu");
        toggle(c, col2X + colW - 36, curY + 12, BameClientConfig.hoverSound, mx, my, delta);
        curY += cardH + cardGap;

        // Row 2: Sound Pack | Hover Style
        renderSettingCard(c, col1X, curY, colW, cardH, "Sound Pack", "Enable/disable sound set");
        String spText = com.bame.client.sound.ClientSoundManager.SOUND_PACKS[Math.clamp(BameClientConfig.soundPack, 0, com.bame.client.sound.ClientSoundManager.SOUND_PACKS.length - 1)] + "  v";
        button(c, spText, col1X + colW - 88, curY + 10, 78, 18, mx, my);

        renderSettingCard(c, col2X, curY, colW, cardH, "Hover Style", "Which hover tick sound to play");
        String hsText = com.bame.client.sound.ClientSoundManager.HOVER_STYLES[Math.clamp(BameClientConfig.hoverStyle, 0, com.bame.client.sound.ClientSoundManager.HOVER_STYLES.length - 1)] + "  v";
        button(c, hsText, col2X + colW - 88, curY + 10, 78, 18, mx, my);
        curY += cardH + cardGap;

        // Row 3: Volume | Hover Volume
        renderSettingCard(c, col1X, curY, colW, cardH, "Volume", "Master volume (" + Math.round(BameClientConfig.soundVolume * 100) + "%)");
        drawCustomSlider(c, col1X + colW - 88, curY + 17, 76, BameClientConfig.soundVolume);

        renderSettingCard(c, col2X, curY, colW, cardH, "Hover Volume", "Hover tick volume (" + Math.round(BameClientConfig.hoverVolume * 100) + "%)");
        drawCustomSlider(c, col2X + colW - 88, curY + 17, 76, BameClientConfig.hoverVolume);
        curY += cardH + 16;

        // Section: KEYBINDS – Übersicht aller belegten Tasten, doppelte rot markiert
        text(c, "KEYBINDS", cx + 2, curY + 2, 0xFF7E8496);
        c.fill(cx + 58, curY + 6, cx + cw, curY + 7, 0xFF202430);
        curY += 14;
        int conflicts = KeybindConflicts.conflictCount();
        text(c, conflicts == 0 ? "No key is used twice. Change keys in the module cards."
                        : conflicts + " module(s) share a key with another module (red).",
                cx + 2, curY + 2, conflicts == 0 ? 0xFF7E8496 : 0xFFF87171);
        curY += 16;

        java.util.List<KeybindConflicts.Entry> bound = new java.util.ArrayList<>();
        for (KeybindConflicts.Entry e : KeybindConflicts.visibleEntries()) {
            if (e.key().getAsInt() >= 0) bound.add(e);
        }
        int rowH = 16;
        for (int i = 0; i < bound.size(); i++) {
            KeybindConflicts.Entry e = bound.get(i);
            int colX = (i % 2 == 0) ? col1X : col2X;
            int rowY = curY + (i / 2) * rowH;
            if (rowY + rowH < cy - 2 || rowY > cy + ch) continue;
            int key = e.key().getAsInt();
            String other = KeybindConflicts.conflictFor(e.id(), key);
            int nameCol = other != null ? 0xFFF87171 : 0xFFD4D8E0;
            String keyName = formatKey(key);
            int kw = textRenderer.getWidth(CustomGuiUtils.getFontText(keyName));
            int keyX = colX + colW - kw - 10;
            CustomGuiUtils.fillUltraRounded(c, keyX - 5, rowY, kw + 10, 13, other != null ? 0xFF2A161B : 0xFF181C24, 3);
            CustomGuiUtils.drawUltraRoundedOutline(c, keyX - 5, rowY, kw + 10, 13, other != null ? 0xFFEF4444 : 0xFF292D36, 3);
            text(c, keyName, keyX, rowY + 3, other != null ? 0xFFFCA5A5 : 0xFFD4D8E0);
            String label = e.name() + (other != null ? "  (also " + other + ")" : "");
            drawHint(c, label, colX + 4, rowY + 3, keyX - 10 - (colX + 4), nameCol);
        }
        curY += ((bound.size() + 1) / 2) * rowH + 8;

        settingsContentHeight = curY - startY;
    }

    /**
     * Geometrie des Item-/Werkzeug-Fensters: höchstens 350x285, bei großer GUI-Skalierung auf den Bildschirm
     * begrenzt (8 px Rand). Spaltenzahl und Rasterhöhe passen sich an, der Inhalt bleibt scrollbar.
     */
    private record ItemModalGeom(int x, int y, int w, int h, int gridX, int gridY, int gridW, int gridH, int cols,
                                 int btnY, int btnW, int btn1X, int btn2X, int btn3X, int scrollbarX) {
        int totalRows(int items) { return (items + cols - 1) / cols; }
        int maxScroll(int items) { return Math.max(0, totalRows(items) * 25 - gridH); }
    }

    private ItemModalGeom itemModalGeom() {
        int w = Math.max(120, Math.min(350, width - 16));
        int h = Math.max(140, Math.min(285, height - 16));
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        int gridX = x + 16;
        int gridY = y + 50;
        int cols = Math.max(1, Math.min(12, (w - 32 - 10) / 25));
        int gridW = cols * 25 - 3;
        int btnY = y + h - 34;
        int gridH = Math.max(25, btnY - 16 - gridY);
        int btnW = Math.max(30, Math.min(100, (w - 32 - 18) / 3));
        int btn1X = x + 16;
        int btn2X = btn1X + btnW + 9;
        int btn3X = btn2X + btnW + 9;
        return new ItemModalGeom(x, y, w, h, gridX, gridY, gridW, gridH, cols, btnY, btnW, btn1X, btn2X, btn3X, x + w - 18);
    }

    private void renderItemModal(DrawContext c, int mx, int my, float delta) {
        ItemModalGeom g = itemModalGeom();
        int modalW = g.w();
        int modalH = g.h();
        int modalX = g.x();
        int modalY = g.y();

        c.fill(0, 0, width, height, 0x99000000);
        CustomGuiUtils.fillUltraRounded(c, modalX, modalY, modalW, modalH, 0xFA14161E, 6);
        CustomGuiUtils.drawUltraRoundedOutline(c, modalX, modalY, modalW, modalH, 0xFF2A2E3D, 6);

        String title = itemModalType == 1 ? "SELECT: TOOLS" : "SELECT: BLOCKS";
        int titleW = textRenderer.getWidth(CustomGuiUtils.getFontText(title));
        c.drawText(textRenderer, CustomGuiUtils.getFontText(title), modalX + (modalW - titleW) / 2, modalY + 10, 0xFFFFFFFF, false);

        int searchX = modalX + 16;
        int searchY = modalY + 24;
        int searchW = modalW - 32;
        int searchH = 20;
        if (itemSearchWidget != null) {
            itemSearchWidget.setX(searchX);
            itemSearchWidget.setY(searchY);
            itemSearchWidget.setWidth(searchW);
            itemSearchWidget.setHeight(searchH);
            itemSearchWidget.render(c, mx, my, delta);
            CustomGuiUtils.drawUltraRoundedOutline(c, searchX, searchY, searchW, searchH, GuiTheme.accent(), 4);
        }

        if (cachedFilteredItems == null) {
            updateItemFilter();
        }
        int gridX = g.gridX();
        int gridY = g.gridY();
        int gridW = g.gridW();
        int gridH = g.gridH();
        int cols = g.cols();

        int totalItems = cachedFilteredItems.size();
        int totalRows = g.totalRows(totalItems);
        int totalContentH = totalRows * 25;
        int maxScroll = g.maxScroll(totalItems);
        itemModalScroll = Math.clamp(itemModalScroll, 0, maxScroll);

        c.enableScissor(gridX, gridY, gridX + gridW + 2, gridY + gridH);

        Item hoveredItem = null;
        int startRow = (int) (itemModalScroll / 25);
        int endRow = Math.min(totalRows, startRow + (gridH / 25) + 2);

        for (int row = startRow; row < endRow; row++) {
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                if (index >= totalItems) break;

                Item item = cachedFilteredItems.get(index);
                int sx = gridX + col * 25;
                int sy = (int) (gridY + row * 25 - itemModalScroll);

                if (sy + 22 < gridY || sy > gridY + gridH) continue;

                String id = Registries.ITEM.getId(item).toString();
                boolean selected = itemModalType == 1 ? tempToolThresholds.containsKey(id) : tempSelectedItems.contains(id);
                boolean hovered = mx >= sx && mx < sx + 22 && my >= sy && my < sy + 22 && my >= gridY && my < gridY + gridH;

                if (hovered) {
                    hoveredItem = item;
                    c.fill(sx, sy, sx + 22, sy + 22, 0x33FFFFFF);
                }

                c.drawItem(new ItemStack(item), sx + 3, sy + 3);

                if (selected) {
                    CustomGuiUtils.drawUltraRoundedOutline(c, sx, sy, 22, 22, GuiTheme.accent(), 3);
                }
            }
        }
        c.disableScissor();

        int scrollbarX = g.scrollbarX();
        int scrollbarW = 4;
        if (maxScroll > 0) {
            box(c, scrollbarX, gridY, scrollbarW, gridH, 0xFF1E212A);
            int thumbH = Math.max(16, (int) ((float) gridH * gridH / totalContentH));
            int thumbY = gridY + (int) ((gridH - thumbH) * (itemModalScroll / maxScroll));
            CustomGuiUtils.fillUltraRounded(c, scrollbarX, thumbY, scrollbarW, thumbH, itemScrollDragging ? 0xFFFFFFFF : GuiTheme.accent(), 2);
        }

        int btnY = g.btnY();
        int btnH = 22;
        int btnW = g.btnW();
        int btn1X = g.btn1X();
        int btn2X = g.btn2X();
        int btn3X = g.btn3X();

        boolean rHover = inside(mx, my, btn1X, btnY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, btn1X, btnY, btnW, btnH, rHover ? 0xFF2A1C20 : 0xFF1B1D25, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, btn1X, btnY, btnW, btnH, rHover ? 0xFFE55757 : 0xFF2A2E3D, 4);
        String rText = "RESET";
        int rW = textRenderer.getWidth(CustomGuiUtils.getFontText(rText));
        c.drawText(textRenderer, CustomGuiUtils.getFontText(rText), btn1X + (btnW - rW) / 2, btnY + 7, 0xFFE55757, false);

        boolean cHover = inside(mx, my, btn2X, btnY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, btn2X, btnY, btnW, btnH, cHover ? 0xFF222632 : 0xFF1B1D25, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, btn2X, btnY, btnW, btnH, cHover ? 0xFFFFFFFF : 0xFF2A2E3D, 4);
        String cText = "CANCEL";
        int cW = textRenderer.getWidth(CustomGuiUtils.getFontText(cText));
        c.drawText(textRenderer, CustomGuiUtils.getFontText(cText), btn2X + (btnW - cW) / 2, btnY + 7, 0xFFD4D8E0, false);

        boolean sHover = inside(mx, my, btn3X, btnY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, btn3X, btnY, btnW, btnH, sHover ? 0xFF1A2B20 : 0xFF1B1D25, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, btn3X, btnY, btnW, btnH, sHover ? 0xFF4ADE80 : 0xFF2A2E3D, 4);
        String sText = "SAVE";
        int sW = textRenderer.getWidth(CustomGuiUtils.getFontText(sText));
        c.drawText(textRenderer, CustomGuiUtils.getFontText(sText), btn3X + (btnW - sW) / 2, btnY + 7, 0xFF4ADE80, false);

        if (hoveredItem != null) {
            c.drawItemTooltip(textRenderer, new ItemStack(hoveredItem), mx, my);
        }
    }

    private int thumbHeight() { return Math.max(24, (int) (ch * (ch / (double) Math.max(ch, contentHeight())))); }
    private int thumbY() { return cy + (int) ((ch - thumbHeight()) * (scroll / Math.max(1, maxScroll()))); }

    private void unfocus() {
        search.setFocused(false);
        if (itemSearchWidget != null) itemSearchWidget.setFocused(false);
        setFocused(null);
    }

    /** Kategorie-Tab der Mod-Ansicht wählen (einzige Quelle: BameClientConfig.selectedCategory, wird gespeichert). */
    private void selectCategory(String category) {
        String cat = BameClientConfig.sanitizeCategory(category);
        if (!cat.equals(BameClientConfig.selectedCategory)) {
            BameClientConfig.selectedCategory = cat;
            BameClientConfig.save();
        }
        scroll = 0;
        listeningMenuBind = false;
        cancelAllKeyListening();
        unfocus();
        layout();
    }

    private void dragScroll(double my) {
        scroll = Math.clamp((my - cy - scrollGrab) / Math.max(1, ch - thumbHeight()) * maxScroll(), 0, maxScroll());
        layout();
    }

    @Override
    public boolean mouseClicked(Click click, boolean twice) {
        geometry();
        layout();
        Click localClick = toPanelClick(click);
        double mx = localClick.x(), my = localClick.y();
        if (localClick.button() != 0) {
            // Rechtsklick: Zyklus-Knöpfe der Karten einen Schritt zurück (z. B. Armor HUD → Style). Nur in der
            // Mod-Ansicht ohne offenen Dialog und nur im Inhaltsbereich (gleicher Bereich wie beim Linksklick).
            if (localClick.button() == 1 && currentView == ViewMode.MODS && !panelModalOpen() && inside(mx, my, cx, cy, cw, ch)) {
                String q = search.getText().trim();
                ColumnTracker tracker = new ColumnTracker(baseY());
                for (ClientTab tab : modTabsForView(q)) {
                    if (tab.mouseRightClicked(this, localClick, getContentLeftX(), getContentRightX(), getColumnWidth(), tracker, q)) {
                        return true;
                    }
                }
            }
            return false;
        }

        if (currentView == ViewMode.HUB) {
            int cyCenter = height / 2;
            int cxCenter = width / 2;

            int count = BameClientConfig.secretUnlocked ? 5 : 4;
            int iconSize = 34;
            int iconGap = 8;
            int totalRowW = count * iconSize + (count - 1) * iconGap;
            int rowStartX = cxCenter - totalRowW / 2;
            int rowY = cyCenter + 20;

            int hoveredQuick = -1;
            for (int i = 0; i < count; i++) {
                int bx = rowStartX + i * (iconSize + iconGap);
                if (inside(mx, my, bx, rowY, iconSize, iconSize)) {
                    hoveredQuick = i;
                    break;
                }
            }

            // Main Button: [ MOD MENU ] (or hovered item)
            int btnW = 240;
            int btnH = 26;
            int btnX = cxCenter - btnW / 2;
            int btnY = cyCenter - 14;
            if (inside(mx, my, btnX, btnY, btnW, btnH)) {
                com.bame.client.sound.ClientSoundManager.playClick();
                if (hoveredQuick == 0) { client.setScreen(new GalleryScreen(this)); return true; }
                else if (hoveredQuick == 1) { currentView = ViewMode.COSMETICS; layout(); }
                else if (hoveredQuick == 3) { openHudEditor(); return true; }
                else if (hoveredQuick == 4 && BameClientConfig.secretUnlocked) { client.setScreen(new SecretScreen(this)); return true; }
                else { currentView = ViewMode.MODS; layout(); }
                return true;
            }

            // Row of Quick Buttons
            for (int i = 0; i < count; i++) {
                int bx = rowStartX + i * (iconSize + iconGap);
                if (inside(mx, my, bx, rowY, iconSize, iconSize)) {
                    com.bame.client.sound.ClientSoundManager.playClick();
                    switch (i) {
                        case 0 -> { client.setScreen(new GalleryScreen(this)); }
                        case 1 -> { currentView = ViewMode.COSMETICS; geometry(); layout(); }
                        case 2 -> { currentView = ViewMode.MODS; geometry(); layout(); }
                        case 3 -> openHudEditor();
                        case 4 -> {
                            if (BameClientConfig.secretUnlocked) {
                                client.setScreen(new SecretScreen(this));
                            }
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        if (itemModalOpen) {
            ItemModalGeom g = itemModalGeom();
            int modalW = g.w();
            int modalH = g.h();
            int modalX = g.x();
            int modalY = g.y();

            if (itemSearchWidget != null) {
                int searchX = modalX + 16;
                int searchY = modalY + 24;
                int searchW = modalW - 32;
                int searchH = 20;
                if (inside(mx, my, searchX, searchY, searchW, searchH)) {
                    itemSearchWidget.mouseClicked(localClick, twice);
                    setFocused(itemSearchWidget);
                    return true;
                }
            }

            int btnY = g.btnY();
            int btnH = 22;
            int btnW = g.btnW();

            if (inside(mx, my, g.btn1X(), btnY, btnW, btnH)) {
                if (itemModalType == 1) {
                    tempToolThresholds.clear();
                } else {
                    tempSelectedItems.clear();
                }
                return true;
            }
            if (inside(mx, my, g.btn2X(), btnY, btnW, btnH)) {
                itemModalOpen = false;
                return true;
            }
            if (inside(mx, my, g.btn3X(), btnY, btnW, btnH)) {
                applyModalSave();
                return true;
            }

            if (cachedFilteredItems == null) updateItemFilter();
            int gridX = g.gridX();
            int gridY = g.gridY();
            int gridW = g.gridW();
            int gridH = g.gridH();
            int cols = g.cols();
            int totalItems = cachedFilteredItems.size();
            int maxScroll = g.maxScroll(totalItems);

            int scrollbarX = g.scrollbarX();
            int scrollbarW = 6;
            if (maxScroll > 0 && inside(mx, my, scrollbarX - 2, gridY, scrollbarW + 4, gridH)) {
                itemScrollDragging = true;
                itemModalScroll = Math.clamp((my - gridY) / (double) gridH * maxScroll, 0, maxScroll);
                return true;
            }

            if (inside(mx, my, gridX, gridY, gridW, gridH)) {
                int relX = (int) mx - gridX;
                int relY = (int) (my - gridY + itemModalScroll);
                int col = relX / 25;
                int row = relY / 25;
                if (col >= 0 && col < cols && (relX % 25) < 22 && (relY % 25) < 22) {
                    int index = row * cols + col;
                    if (index >= 0 && index < totalItems) {
                        Item item = cachedFilteredItems.get(index);
                        String id = Registries.ITEM.getId(item).toString();
                        if (itemModalType == 1) {
                            if (tempToolThresholds.containsKey(id)) {
                                tempToolThresholds.remove(id);
                            } else {
                                tempToolThresholds.put(id, 10);
                            }
                        } else {
                            if (tempSelectedItems.contains(id)) {
                                tempSelectedItems.remove(id);
                            } else {
                                tempSelectedItems.add(id);
                            }
                        }
                        return true;
                    }
                }
                return true;
            }

            if (!inside(mx, my, modalX, modalY, modalW, modalH)) {
                applyModalSave();
                return true;
            }

            return true;
        }

        if (anyColorPickerOpen()) {
            int CP_W = 150;
            int CP_H = 100;
            int myCpX = (width - CP_W) / 2;
            int myCpY = (height - CP_H) / 2;
            if (inside(mx, my, myCpX + CP_W - 16, myCpY + 4, 14, 14)) {
                crosshairColorPickerOpen = false;
                hitColorColorPickerOpen = false;
                hitboxColorPickerOpen = false;
                blockOutlineColorPickerOpen = false;
                genericColorPickerOpen = false;
                cpDrag = -1;
                return true;
            }
            int svX = myCpX + 46;
            int svY = myCpY + 22;
            int svW = 92;
            int svH = 30;
            if (inside(mx, my, svX, svY, svW, svH)) {
                cpDrag = 0;
                updateModalColor(mx, my);
                return true;
            }
            int barX = myCpX + 10;
            int barW = 128;
            int hueY = myCpY + 58;
            int barH = 8;
            if (inside(mx, my, barX, hueY, barW, barH)) {
                cpDrag = 1;
                updateModalColor(mx, my);
                return true;
            }
            if (inside(mx, my, myCpX, myCpY, CP_W, CP_H)) {
                return true;
            }
            crosshairColorPickerOpen = false;
            hitColorColorPickerOpen = false;
            hitboxColorPickerOpen = false;
            blockOutlineColorPickerOpen = false;
            genericColorPickerOpen = false;
            cpDrag = -1;
            return true;
        }

        if (currentView == ViewMode.COSMETICS) {
            if (ShopOverlay.isModalOpen()) {
                return ShopOverlay.handleModalClick(this, mx, my);
            }
            if (CosmeticsTab.customSkinModalOpen) {
                return CosmeticsTab.handleModalClick(this, localClick, twice);
            }
            // Dedicated Sidebar Categories Clicks
            int catBtnW = sidebar - 16;
            for (CosmeticsTab.SidebarRow row : cosmeticsSidebarRows()) {
                if (inside(mx, my, px + 8, row.y(), catBtnW, row.height())) {
                    // Ansicht wechseln, Dropdown auf-/zuklappen bzw. zum Abschnitt springen (setzt den Scroll-Stand)
                    CosmeticsTab.onSidebarClick(this, row.entry(), getSearchQuery());
                    com.bame.client.sound.ClientSoundManager.playClick();
                    layout();
                    return true;
                }
            }

            // Dedicated Back Button
            int backBtnH = 26;
            int backBtnY = py + ph - 36;
            if (inside(mx, my, px + 8, backBtnY, catBtnW, backBtnH)) {
                currentView = ViewMode.MODS;
                scroll = 0;
                com.bame.client.sound.ClientSoundManager.playClick();
                geometry();
                layout();
                return true;
            }

            // Top-Right: Quick Unequip All Button Click
            int unequipW = 110;
            int unequipH = 20;
            int unequipX = cx + cw - unequipW;
            int unequipY = py + 8;
            if (inside(mx, my, unequipX, unequipY, unequipW, unequipH)) {
                if ("EMOTES".equalsIgnoreCase(CosmeticsTab.subCategory)) {
                    java.util.Arrays.fill(com.bame.client.cosmetics.CosmeticsConfig.wheelSlots, null);
                    com.bame.client.cosmetics.CosmeticsConfig.syncEquippedEmotesFromSlots();
                    com.bame.client.cosmetics.CosmeticsConfig.save();
                } else if (CosmeticsTab.isKillEffectsView()) {
                    // Kill-Effekt-Ansicht: nur den Kill-Effekt ablegen
                    com.bame.client.cosmetics.CosmeticsConfig.setKillEffect("none");
                } else {
                    com.bame.client.cosmetics.CosmeticsConfig.setWings("none");
                    com.bame.client.cosmetics.CosmeticsConfig.setHead("none");
                    com.bame.client.cosmetics.CosmeticsConfig.setFace("none");
                    com.bame.client.cosmetics.CosmeticsConfig.setBack("none");
                    com.bame.client.cosmetics.CosmeticsConfig.accessories.clear();
                    com.bame.client.cosmetics.CosmeticsConfig.setPet("none", "");
                    CosmeticsTab.resetPreview();
                }
                com.bame.client.sound.ClientSoundManager.playClick();
                return true;
            }

            // Scrollbar dragging
            if (maxScroll() > 0 && inside(mx, my, cx + cw - 2, cy, 14, ch)) {
                unfocus();
                scrollDragging = true;
                scrollGrab = (inside(mx, my, cx + cw - 2, thumbY(), 14, thumbHeight())) ? my - thumbY() : thumbHeight() / 2.0;
                dragScroll(my);
                return true;
            }

            // Search box
            unfocus();
            if (search.mouseClicked(localClick, twice)) {
                setFocused(search);
                return true;
            }

            // Content Area Clicks
            ClientTab cosTab = tabs.get(COSMETICS_TAB);
            if (cosTab != null) {
                int columnWidth = getColumnWidth();
                int leftX = getContentLeftX();
                int rightX = getContentRightX();
                String q = search.getText().trim();
                ColumnTracker tracker = new ColumnTracker(baseY());
                if (cosTab.mouseClicked(this, localClick, twice, leftX, rightX, columnWidth, tracker, q)) {
                    return true;
                }
            }
            return false;
        }

        // Ein Klick beendet ein offenes Warten auf eine Taste (der Knopf selbst startet es gleich neu)
        listeningMenuBind = false;
        cancelAllKeyListening();

        // Left vertical sidebar clicks (MODS, COSMETICS, SKINS, GALLERY, SETTINGS, HUB)
        if (clickSidebar(mx, my)) {
            return true;
        }

        if (currentView == ViewMode.MODS) {
            // Category tabs
            HeaderLayout hl = modsHeaderLayout();
            for (int i = 0; i < CATEGORIES.length; i++) {
                if (inside(mx, my, hl.tabX()[i], py + 9, hl.tabW()[i], 18)) {
                    selectCategory(CATEGORIES[i]);
                    com.bame.client.sound.ClientSoundManager.playClick();
                    return true;
                }
            }

            if (maxScroll() > 0 && inside(mx, my, cx + cw - 2, cy, 14, ch)) {
                unfocus();
                scrollDragging = true;
                scrollGrab = (inside(mx, my, cx + cw - 2, thumbY(), 14, thumbHeight())) ? my - thumbY() : thumbHeight() / 2.0;
                dragScroll(my);
                return true;
            }
            unfocus();
            if (search.mouseClicked(localClick, twice)) {
                setFocused(search);
                return true;
            }
            if (!inside(mx, my, cx, cy, cw, ch)) return false;

            int columnWidth = getColumnWidth();
            int leftX = getContentLeftX();
            int rightX = getContentRightX();
            String q = search.getText().trim();
            ColumnTracker tracker = new ColumnTracker(baseY());
            for (ClientTab tab : modTabsForView(q)) {
                if (tab.mouseClicked(this, localClick, twice, leftX, rightX, columnWidth, tracker, q)) {
                    return true;
                }
            }
        } else if (currentView == ViewMode.THEME || currentView == ViewMode.SETTINGS) {
            if (clickSettingsHeader(mx, my)) {
                return true;
            }
            if (maxScroll() > 0 && inside(mx, my, cx + cw - 2, cy, 14, ch)) {
                scrollDragging = true;
                scrollGrab = (inside(mx, my, cx + cw - 2, thumbY(), 14, thumbHeight())) ? my - thumbY() : thumbHeight() / 2.0;
                dragScroll(my);
                return true;
            }
            if (!inside(mx, my, cx - 4, cy - 2, cw + 8, ch + 2)) return false;

            if (currentView == ViewMode.THEME) {
                int y = baseY();
                int cols = columns(), tw = (cw - 8 * (cols - 1)) / cols;
                for (int i = 0; i < GuiTheme.PRESETS.length; i++) {
                    if (inside(mx, my, cx + i % cols * (tw + 8), y + 44 + i / cols * 58, tw, 50)) {
                        BameClientConfig.theme = GuiTheme.PRESETS[i].name();
                        BameClientConfig.customMainColor = false;
                        com.bame.client.sound.ClientSoundManager.playClick();
                        themeSettings.close();
                        BameClientConfig.save();
                        layout();
                        return true;
                    }
                }
                if (themeSettings.click(mx, my)) {
                    layout();
                    return true;
                }
                return false;
            }

            // GENERAL (gleiche Positionen wie renderSettings)
            int colW = (cw - 12) / 2;
            int col1X = cx;
            int col2X = cx + colW + 12;
            int cardH = 38;
            int cardGap = 8;
            int curY = baseY() + 4;
            curY += 16;

            if (inside(mx, my, col1X + colW - 68, curY + 10, 58, 18)) {
                listeningMenuBind = true;
                com.bame.client.sound.ClientSoundManager.playClick();
                return true;
            }
            if (inside(mx, my, col2X + colW - 36, curY + 12, 26, 14)) {
                BameClientConfig.threeDGui = !BameClientConfig.threeDGui;
                com.bame.client.sound.ClientSoundManager.playClick();
                BameClientConfig.save();
                geometry();
                layout();
                return true;
            }
            curY += cardH + cardGap;

            if (inside(mx, my, col1X + colW - 88, curY + 10, 78, 18)) {
                BameClientConfig.guiAnimation = (BameClientConfig.guiAnimation + 1) % 5;
                com.bame.client.sound.ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }
            if (inside(mx, my, col2X + colW - 36, curY + 12, 26, 14)) {
                BameClientConfig.notifications = !BameClientConfig.notifications;
                com.bame.client.notification.NotificationManager.showModuleToggle("Notifications", BameClientConfig.notifications);
                com.bame.client.sound.ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }
            curY += cardH + 16;
            curY += 16;

            if (inside(mx, my, col1X + colW - 36, curY + 12, 26, 14)) {
                BameClientConfig.moduleSound = !BameClientConfig.moduleSound;
                com.bame.client.sound.ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }
            if (inside(mx, my, col2X + colW - 36, curY + 12, 26, 14)) {
                BameClientConfig.hoverSound = !BameClientConfig.hoverSound;
                com.bame.client.sound.ClientSoundManager.playHover("toggle_hover_sound");
                BameClientConfig.save();
                return true;
            }
            curY += cardH + cardGap;

            if (inside(mx, my, col1X + colW - 88, curY + 10, 78, 18)) {
                BameClientConfig.soundPack = (BameClientConfig.soundPack + 1) % com.bame.client.sound.ClientSoundManager.SOUND_PACKS.length;
                com.bame.client.sound.ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }
            if (inside(mx, my, col2X + colW - 88, curY + 10, 78, 18)) {
                BameClientConfig.hoverStyle = (BameClientConfig.hoverStyle + 1) % com.bame.client.sound.ClientSoundManager.HOVER_STYLES.length;
                com.bame.client.sound.ClientSoundManager.playHover("style_preview_" + BameClientConfig.hoverStyle);
                BameClientConfig.save();
                return true;
            }
            curY += cardH + cardGap;

            if (inside(mx, my, col1X + colW - 92, curY + 10, 84, 18)) {
                draggingSoundVolume = true;
                float fval = (float) Math.clamp((mx - (col1X + colW - 88)) / 76.0, 0.0, 1.0);
                BameClientConfig.soundVolume = Math.round(fval * 100f) / 100f;
                com.bame.client.sound.ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }
            if (inside(mx, my, col2X + colW - 92, curY + 10, 84, 18)) {
                draggingHoverVolume = true;
                float fval = (float) Math.clamp((mx - (col2X + colW - 88)) / 76.0, 0.0, 1.0);
                BameClientConfig.hoverVolume = Math.round(fval * 100f) / 100f;
                com.bame.client.sound.ClientSoundManager.playHover("volume_preview");
                BameClientConfig.save();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        Click localClick = toPanelClick(click);

        if ((anyColorPickerOpen()) && cpDrag >= 0) {
            updateModalColor(localClick.x(), localClick.y());
            return true;
        }
        if (themeSettings.dragging()) {
            themeSettings.drag(localClick.x(), localClick.y());
            return true;
        }
        if (scrollDragging) {
            dragScroll(localClick.y());
            return true;
        }
        if (itemModalOpen && itemScrollDragging) {
            ItemModalGeom g = itemModalGeom();
            if (cachedFilteredItems != null) {
                int maxScroll = g.maxScroll(cachedFilteredItems.size());
                if (maxScroll > 0) {
                    itemModalScroll = Math.clamp((localClick.y() - g.gridY()) / (double) g.gridH() * maxScroll, 0, maxScroll);
                }
            }
            return true;
        }

        if (draggingSoundVolume) {
            int colW = (cw - 12) / 2;
            int col1X = cx;
            int sx = col1X + colW - 88;
            float fval = (float) Math.clamp(((localClick.x() - sx) / 76.0), 0.0, 1.0);
            BameClientConfig.soundVolume = Math.round(fval * 100f) / 100f;
            return true;
        }
        if (draggingHoverVolume) {
            int colW = (cw - 12) / 2;
            int col2X = cx + colW + 12;
            int sx = col2X + colW - 88;
            float fval = (float) Math.clamp(((localClick.x() - sx) / 76.0), 0.0, 1.0);
            BameClientConfig.hoverVolume = Math.round(fval * 100f) / 100f;
            return true;
        }

        if (currentView == ViewMode.COSMETICS) {
            ClientTab cosTab = tabs.get(COSMETICS_TAB);
            if (cosTab != null && cosTab.mouseDragged(this, localClick, dx, dy)) {
                return true;
            }
        } else {
            // Jeder Tab reagiert nur, wenn er selbst gerade einen Regler zieht – deshalb unabhängig vom aktiven Tab
            for (ClientTab tab : allModTabs()) {
                if (tab.mouseDragged(this, localClick, dx, dy)) {
                    return true;
                }
            }
        }

        return super.mouseDragged(localClick, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        Click localClick = toPanelClick(click);

        cpDrag = -1;
        itemScrollDragging = false;
        boolean volumeDrag = draggingSoundVolume || draggingHoverVolume;
        boolean handled = scrollDragging || themeSettings.dragging() || volumeDrag;
        scrollDragging = false;
        draggingSoundVolume = false;
        draggingHoverVolume = false;

        for (ClientTab tab : tabs.values()) {
            if (tab.mouseReleased(this, localClick)) {
                handled = true;
            }
        }

        // Nur echte Änderungen merken; geschrieben wird gebündelt im Hintergrund (BameClientConfig.tick/flush)
        if (volumeDrag) BameClientConfig.save();
        themeSettings.release();
        return handled || super.mouseReleased(localClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        double[] localPos = panel3D.toLocal(mx, my);
        double lmx = localPos[0], lmy = localPos[1];

        if (currentView == ViewMode.COSMETICS && ShopOverlay.isModalOpen()) {
            return true;
        }
        if (itemModalOpen) {
            ItemModalGeom g = itemModalGeom();
            if (inside(lmx, lmy, g.x(), g.y(), g.w(), g.h())) {
                if (cachedFilteredItems != null) {
                    int maxScroll = g.maxScroll(cachedFilteredItems.size());
                    if (maxScroll > 0) {
                        itemModalScroll = Math.clamp(itemModalScroll - vertical * 25, 0, maxScroll);
                    }
                }
                return true;
            }
            return true;
        }
        if (currentView != ViewMode.HUB && inside(lmx, lmy, cx, cy, cw + 14, ch) && !themeSettings.dragging()) {
            if (currentView == ViewMode.COSMETICS) {
                ClientTab cosTab = tabs.get(COSMETICS_TAB);
                if (cosTab != null && cosTab.mouseScrolled(lmx, lmy, horizontal, vertical)) return true;
            } else if (currentView == ViewMode.MODS) {
                for (ClientTab tab : modTabsForView(getSearchQuery())) {
                    if (tab.mouseScrolled(lmx, lmy, horizontal, vertical)) return true;
                }
            }
            scroll = Math.clamp(scroll - vertical * 26, 0, maxScroll());
            layout();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (currentView == ViewMode.COSMETICS && ShopOverlay.handleKey(input)) {
            return true;
        }
        if (currentView == ViewMode.COSMETICS && CosmeticsTab.customSkinModalOpen) {
            if (CosmeticsTab.handleModalKeyPressed(this, input)) {
                return true;
            }
        }
        if (itemModalOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                applyModalSave();
                return true;
            }
            if (itemSearchWidget != null && itemSearchWidget.isFocused()) {
                if (itemSearchWidget.keyPressed(input)) return true;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && (anyColorPickerOpen())) {
            crosshairColorPickerOpen = false;
            hitColorColorPickerOpen = false;
            hitboxColorPickerOpen = false;
            blockOutlineColorPickerOpen = false;
            genericColorPickerOpen = false;
            cpDrag = -1;
            return true;
        }

        // Menütaste belegen: Escape = abbrechen (Taste bleibt), Entf/Rücktaste = entfernen, sonst neue Taste
        if (listeningMenuBind) {
            listeningMenuBind = false;
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                com.bame.client.notification.NotificationManager.showInfo("Menu Key", "Unchanged: " + formatKey(BameClientConfig.menuBind));
            } else if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                BameClientConfig.menuBind = -1;
                BameClientConfig.save();
                com.bame.client.notification.NotificationManager.showInfo("Menu Key", "Removed - open the menu with /caeserclient");
            } else {
                BameClientConfig.menuBind = key;
                BameClientConfig.save();
                KeybindConflicts.notifyIfConflict(KeybindConflicts.MENU_ID, key);
            }
            com.bame.client.sound.ClientSoundManager.playClick();
            return true;
        }

        // Modul-Taste belegen: der wartende Tab bekommt die Taste zuerst (auch Escape = Belegung entfernen),
        // danach wird das Warten immer beendet
        for (ClientTab tab : tabs.values()) {
            if (tab.isListeningForKey()) {
                tab.keyPressed(this, input);
                cancelAllKeyListening();
                if (key != GLFW.GLFW_KEY_ESCAPE) KeybindConflicts.notifyIfKeyShared(key);
                BameClientConfig.save();
                return true;
            }
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            themeSettings.close();
            if (currentView != ViewMode.HUB) {
                currentView = ViewMode.HUB;
                layout();
                return true;
            }
        }
        // Geheime Kombination nie auswerten, solange ein Textfeld fokussiert ist (z. B. Kopieren in der Suche)
        if (!isAnyTextFieldFocused() && isSecretComboPressed(input)) {
            BameClientConfig.secretUnlocked = !BameClientConfig.secretUnlocked;
            com.bame.client.sound.ClientSoundManager.playClick();
            return true;
        }
        if (search.isFocused()) {
            if (search.keyPressed(input)) {
                layout();
                return true;
            }
        }
        for (ClientTab tab : tabs.values()) {
            if (tab.keyPressed(this, input)) return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (currentView == ViewMode.COSMETICS && CosmeticsTab.customSkinModalOpen) {
            if (CosmeticsTab.handleModalCharTyped(this, input)) {
                return true;
            }
        }
        if (itemModalOpen && itemSearchWidget != null && itemSearchWidget.isFocused()) {
            return itemSearchWidget.charTyped(input);
        }
        if (search.isFocused()) {
            boolean r = search.charTyped(input);
            layout();
            return r;
        }
        for (ClientTab tab : tabs.values()) {
            if (tab.charTyped(this, input)) return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        CosmeticsTab.resetPreview();
        ShopOverlay.close();
        themeSettings.close();
        listeningMenuBind = false;
        cancelAllKeyListening();
        // Beim Schließen des Menüs ausstehende Änderungen sofort (im Hintergrund) schreiben
        BameClientConfig.flush();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        super.close();
        openTime = 0;
        BameClientConfig.flush();
    }
}
