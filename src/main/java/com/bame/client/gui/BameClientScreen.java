package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import com.bame.client.gui.tab.*;
import com.bame.client.module.*;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PlayerSkinWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.DefaultSkinHelper;
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
    public static String selectedCategory = "Combat";
    public static String selected = "Combat";

    private final Map<String, ClientTab> tabs = new LinkedHashMap<>();

    private boolean listeningMenuBind = false;
    private boolean scrollDragging = false;
    private boolean draggingSoundVolume = false;
    private boolean draggingHoverVolume = false;

    // HSV Color Picker Modal
    private boolean crosshairColorPickerOpen = false;
    private boolean hitColorColorPickerOpen = false;
    private boolean hitboxColorPickerOpen = false;
    private boolean blockOutlineColorPickerOpen = false;
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
    private PlayerSkinWidget leftSkinWidget;

    public BameClientScreen() {
        super(Text.literal("Caeser Client"));
        tabs.put("Combat", new CombatTab());
        tabs.put("Movement", new MovementTab());
        tabs.put("Visuals", new RenderTab());
        tabs.put("Misc", new MiscTab());
        tabs.put("World", new WorldTab());
        tabs.put("Cosmetics", new CosmeticsTab());
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
            c.fill(chevX, chevY, chevX + 5, chevY + 1, 0xFF656B7C);
            c.fill(chevX + 1, chevY + 1, chevX + 4, chevY + 2, 0xFF656B7C);
            c.fill(chevX + 2, chevY + 2, chevX + 3, chevY + 3, 0xFF656B7C);
        }

        int maxDescW = w - 90;
        String d = desc;
        if (textRenderer.getWidth(CustomGuiUtils.getFontText(d)) > maxDescW) {
            while (d.length() > 3 && textRenderer.getWidth(CustomGuiUtils.getFontText(d + "...")) > maxDescW) {
                d = d.substring(0, d.length() - 1);
            }
            d = d + "...";
        }
        c.drawText(textRenderer, CustomGuiUtils.getFontText(d), x + 48, descY, 0xFF8E95A4, false);

        int toggleX = x + w - 38;
        int toggleY = y + 17;
        new CustomToggleWidget(toggleX, toggleY, 26, 14, enabled).render(c, mx, my, 0);

        if (expanded) {
            c.fill(x + 8, y + 49, x + w - 8, y + 50, 0xFF242735);
        }
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

    public void toggle(DrawContext c, int x, int y, boolean on, int mx, int my, float delta) {
        new CustomToggleWidget(x, y, 26, 14, on).render(c, mx, my, delta);
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
        if (type == 0) syncCrosshairHsv();
        else if (type == 1) syncHitColorHsv();
        else if (type == 2) syncHitboxHsv();
        else if (type == 3) syncBlockOutlineHsv();
    }

    public int getColumnWidth() { return (cw - 16) / 2; }
    public int getContentLeftX() { return cx; }
    public int getContentRightX() { return cx + (cw - 16) / 2 + 16; }
    public int getCy() { return cy; }
    public int getCh() { return ch; }

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
            ch = ph - 54;
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
            ch = ph - 54;
            if (search != null) {
                search.setX(cx + cw - 128);
                search.setY(py + 9);
                search.setWidth(120);
                search.setHeight(20);
            }
        }
        updateLeftSkinGeometry();
    }

    @Override
    protected void init() {
        if (openTime == 0) openTime = System.currentTimeMillis();
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

        initLeftSkinWidget();
        layout();
    }

    private void initLeftSkinWidget() {
        if (client == null || client.getLoadedEntityModels() == null) return;
        int leftW = Math.max(100, px - 20);
        int skinW = Math.clamp(leftW, 130, 200);
        int skinH = Math.clamp(ph - 50, 220, 290);
        int skinX = Math.max(10, (px - skinW) / 2);
        int skinY = py + (ph - skinH) / 2 + 16;

        leftSkinWidget = new PlayerSkinWidget(skinW, skinH, client.getLoadedEntityModels(), () -> {
            if (SkinProtectModule.enabled) {
                return SkinProtectModule.getCurrentSkin();
            }
            return (client.player != null) ? client.player.getSkin() : DefaultSkinHelper.getSteve();
        });
        leftSkinWidget.setPosition(skinX, skinY);
        setSkinWidgetInitialRotation(leftSkinWidget, -22.0f);
    }

    private void updateLeftSkinGeometry() {
        if (!BameClientConfig.threeDGui) return;
        if (leftSkinWidget == null) {
            initLeftSkinWidget();
            return;
        }
        int leftW = Math.max(100, px - 20);
        int skinW = Math.clamp(leftW, 130, 200);
        int skinH = Math.clamp(ph - 50, 220, 290);
        int skinX = Math.max(10, (px - skinW) / 2);
        int skinY = py + (ph - skinH) / 2 + 16;
        leftSkinWidget.setDimensionsAndPosition(skinW, skinH, skinX, skinY);
    }

    private void setSkinWidgetInitialRotation(PlayerSkinWidget widget, float yRot) {
        if (widget == null) return;
        try {
            for (java.lang.reflect.Field f : PlayerSkinWidget.class.getDeclaredFields()) {
                if (f.getType() == float.class && (f.getName().equals("yRotation") || f.getName().contains("yRot"))) {
                    f.setAccessible(true);
                    f.setFloat(widget, yRot);
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    private void renderLeftSkin(DrawContext c, int mx, int my, float delta, float p) {
        if (!BameClientConfig.threeDGui || px < 100) return;
        if (leftSkinWidget == null) initLeftSkinWidget();
        if (leftSkinWidget == null) return;

        int leftCenterX = px / 2;

        // Player Name Badge (NameProtect integration: alias when enabled, own name when disabled)
        String displayName = (client != null && client.player != null) ? client.player.getName().getString() : "Player";
        if (NameProtectModule.enabled) {
            displayName = NameProtectModule.getProtectedName(displayName);
        }
        int nameW = textRenderer.getWidth(CustomGuiUtils.getFontText(displayName));
        int badgeW = nameW + 16;
        int badgeH = 15;
        int badgeX = leftCenterX - badgeW / 2;
        int badgeY = py + 12;
        CustomGuiUtils.fillUltraRounded(c, badgeX, badgeY, badgeW, badgeH, 0xD0131722, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, badgeX, badgeY, badgeW, badgeH, GuiTheme.alpha(GuiTheme.accent(), 110), 4);
        c.drawText(textRenderer, CustomGuiUtils.getFontText(displayName), badgeX + 8, badgeY + 4, GuiTheme.accent(), false);

        // Pedestal & Drop Shadow under feet
        int skinW = leftSkinWidget.getWidth();
        int skinH = leftSkinWidget.getHeight();
        int pedW = Math.min(130, (int) (skinW * 0.72));
        int pedX = leftCenterX - pedW / 2;
        int pedY = leftSkinWidget.getY() + skinH - 14;

        CustomGuiUtils.fillUltraRounded(c, pedX - 10, pedY + 2, pedW + 24, 10, 0x45000000, 4);
        CustomGuiUtils.fillUltraRounded(c, pedX, pedY, pedW, 6, 0x30000000 | (GuiTheme.accent() & 0x00FFFFFF), 3);
        CustomGuiUtils.drawUltraRoundedOutline(c, pedX, pedY, pedW, 6, GuiTheme.alpha(GuiTheme.accent(), 85), 3);

        // 3D Player Model
        leftSkinWidget.render(c, mx, my, delta);

        // Equipped Cosmetics Badges on 3D Player Preview
        int cosBadgeY = pedY + 12;
        if (!"none".equalsIgnoreCase(com.bame.client.cosmetics.CosmeticsConfig.wings)) {
            String wName = "angel".equalsIgnoreCase(com.bame.client.cosmetics.CosmeticsConfig.wings) ? "🪽 GOLD WINGS" : "🪽 DRAGON WINGS";
            int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(wName));
            int bx = leftCenterX - (tw + 12) / 2;
            CustomGuiUtils.fillUltraRounded(c, bx, cosBadgeY, tw + 12, 13, 0xD0131722, 3.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, bx, cosBadgeY, tw + 12, 13, 0xFFF59E0B, 3.0f);
            c.drawText(textRenderer, CustomGuiUtils.getFontText(wName), bx + 6, cosBadgeY + 3, 0xFFFDE047, false);
            cosBadgeY += 15;
        }
        if (!"none".equalsIgnoreCase(com.bame.client.cosmetics.CosmeticsConfig.head)) {
            String hName = "halo".equalsIgnoreCase(com.bame.client.cosmetics.CosmeticsConfig.head) ? "👑 ANGEL HALO" : "😈 DEMON HORNS";
            int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(hName));
            int bx = leftCenterX - (tw + 12) / 2;
            CustomGuiUtils.fillUltraRounded(c, bx, cosBadgeY, tw + 12, 13, 0xD0131722, 3.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, bx, cosBadgeY, tw + 12, 13, 0xFFEF4444, 3.0f);
            c.drawText(textRenderer, CustomGuiUtils.getFontText(hName), bx + 6, cosBadgeY + 3, 0xFFFCA5A5, false);
            cosBadgeY += 15;
        }
        if (!"none".equalsIgnoreCase(com.bame.client.cosmetics.CosmeticsConfig.pet)) {
            String target = com.bame.client.cosmetics.CosmeticsConfig.petCustomPlayer;
            String pName = "self".equalsIgnoreCase(com.bame.client.cosmetics.CosmeticsConfig.pet) ? "🐾 MINI-ME" : ("🐾 " + ((target != null && !target.isEmpty()) ? target.toUpperCase(java.util.Locale.ROOT) : "PLAYER PET"));
            int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(pName));
            int bx = leftCenterX - (tw + 12) / 2;
            CustomGuiUtils.fillUltraRounded(c, bx, cosBadgeY, tw + 12, 13, 0xD0131722, 3.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, bx, cosBadgeY, tw + 12, 13, GuiTheme.accent(), 3.0f);
            c.drawText(textRenderer, CustomGuiUtils.getFontText(pName), bx + 6, cosBadgeY + 3, GuiTheme.accent(), false);
        }
    }

    private int columns() { return 4; }
    private int effectsY() { return 44 + ((GuiTheme.PRESETS.length + columns() - 1) / columns()) * 58 + 18; }
    private int settingsY() { return effectsY(); }

    private String getCategoryStats(String cat) {
        switch (cat) {
            case "Combat" -> {
                int on = (AutoClickerModule.enabled ? 1 : 0) + (ReachDisplayModule.enabled ? 1 : 0)
                        + (HitColorModule.enabled ? 1 : 0) + (LowShieldModule.enabled ? 1 : 0)
                        + (CustomHitboxesModule.enabled ? 1 : 0);
                return "5 modules  •  " + on + " on";
            }
            case "Movement" -> {
                int on = (InvMoveModule.enabled ? 1 : 0) + (FreelookModule.enabled ? 1 : 0) + (com.bame.client.module.EmoteModule.enabled ? 1 : 0);
                return "3 modules  •  " + on + " on";
            }
            case "Visuals" -> {
                int on = (FullbrightModule.enabled ? 1 : 0) + (ShowHudModule.enabled ? 1 : 0)
                        + (ZoomModule.enabled ? 1 : 0) + (SpotifyHudModule.enabled ? 1 : 0)
                        + (ScoreboardModule.enabled ? 1 : 0) + (CustomCrosshairModule.enabled ? 1 : 0)
                        + (BlockOutlineModule.enabled ? 1 : 0) + (ItemSizeModule.enabled ? 1 : 0)
                        + (HandPositionModule.enabled ? 1 : 0) + (TabBadgeModule.enabled ? 1 : 0);
                return "10 modules  •  " + on + " on";
            }
            case "Misc" -> {
                int on = (AutoToolModule.enabled ? 1 : 0) + (DurabilityGuardModule.enabled ? 1 : 0);
                return "2 modules  •  " + on + " on";
            }
            case "World" -> {
                int on = (AutoAreaMinerModule.enabled ? 1 : 0) + (TimeChangerModule.enabled ? 1 : 0)
                        + (NoFogModule.enabled ? 1 : 0);
                return "3 modules  •  " + on + " on";
            }
            case "Cosmetics" -> {
                int on = (!"none".equalsIgnoreCase(com.bame.client.cosmetics.CosmeticsConfig.wings) ? 1 : 0)
                        + (!"none".equalsIgnoreCase(com.bame.client.cosmetics.CosmeticsConfig.head) ? 1 : 0)
                        + (!"none".equalsIgnoreCase(com.bame.client.cosmetics.CosmeticsConfig.pet) ? 1 : 0);
                return "3 slots  •  " + on + " on";
            }
            default -> { return ""; }
        }
    }

    private int contentHeight() {
        if (currentView == ViewMode.THEME) return settingsY() + themeSettings.height() + 8;
        if (currentView == ViewMode.SETTINGS) return 360;
        if (currentView == ViewMode.COSMETICS) {
            ClientTab cosTab = tabs.get("Cosmetics");
            if (cosTab != null) {
                ColumnTracker tracker = new ColumnTracker(0);
                cosTab.calculateHeight(this, getColumnWidth(), tracker, search != null ? search.getText().trim() : "");
                return Math.max(tracker.leftY, tracker.rightY);
            }
            return 400;
        }
        String q = (search != null) ? search.getText().trim() : "";
        ColumnTracker tracker = new ColumnTracker(0);
        if (selectedCategory.equalsIgnoreCase("MODS") || !q.isEmpty()) {
            for (Map.Entry<String, ClientTab> entry : tabs.entrySet()) {
                if (entry.getKey().equals("Cosmetics")) continue;
                entry.getValue().calculateHeight(this, getColumnWidth(), tracker, q);
            }
        } else {
            ClientTab activeTab = tabs.get(selectedCategory);
            if (activeTab != null) {
                activeTab.calculateHeight(this, getColumnWidth(), tracker, q);
            }
        }
        return Math.max(tracker.leftY, tracker.rightY);
    }

    private double maxScroll() { return Math.max(0, contentHeight() - ch); }
    public int baseY() { return cy - (int) scroll; }

    private void layout() {
        scroll = Math.clamp(scroll, 0, maxScroll());
        int columnWidth = getColumnWidth();
        int leftX = getContentLeftX();
        int rightX = getContentRightX();
        String q = (search != null) ? search.getText().trim() : "";
        ColumnTracker tracker = new ColumnTracker(baseY());

        if (currentView == ViewMode.COSMETICS) {
            ClientTab cosTab = tabs.get("Cosmetics");
            if (cosTab != null) {
                cosTab.layout(this, leftX, rightX, columnWidth, tracker, q);
            }
            return;
        }

        if (currentView == ViewMode.THEME) {
            themeSettings.layout(cx, baseY() + settingsY(), cw);
            return;
        }

        if (selectedCategory.equalsIgnoreCase("MODS") || !q.isEmpty()) {
            for (Map.Entry<String, ClientTab> entry : tabs.entrySet()) {
                if (entry.getKey().equals("Cosmetics")) continue;
                entry.getValue().layout(this, leftX, rightX, columnWidth, tracker, q);
            }
        } else {
            ClientTab activeTab = tabs.get(selectedCategory);
            if (activeTab != null) {
                activeTab.layout(this, leftX, rightX, columnWidth, tracker, q);
            }
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
        int wLogo = 14;
        int wClient = textRenderer.getWidth(CustomGuiUtils.getFontText("CLIENT"));
        int totalLogoW = wCaeser + 6 + wLogo + 6 + wClient;
        int startX = -totalLogoW / 2;
        c.drawText(textRenderer, CustomGuiUtils.getFontText("CAESER"), startX, 0, 0xFFFFFFFF, false);
        CustomGuiUtils.drawCLogo(c, startX + wCaeser + 6, -1, 14, 0xFFFFFFFF);
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
        if (currentView == ViewMode.HUB) {
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

        int bg = BameClientConfig.seeThrough ? 0xD810121A : 0xF710121A;
        CustomGuiUtils.fillUltraRounded(c, px, py, pw, ph, bg, 8.0f);

        if (BameClientConfig.customWallpaper) {
            com.bame.client.wallpaper.WallpaperManager.render(c, px, py, pw, ph, p);
        }

        CustomGuiUtils.drawUltraRoundedOutline(c, px, py, pw, ph, 0xFF242834, 8.0f);

        // Left Vertical Sidebar
        c.fill(px + sidebar - 1, py, px + sidebar, py + ph, 0xFF232530);
        int accent = GuiTheme.accent();

        if (currentView == ViewMode.COSMETICS) {
            // Dedicated Clean Cosmetics Sidebar (Matching NoRisk Client Structure)
            CustomGuiUtils.drawShirtIcon(c, px + 12, py + 14, accent);
            c.drawText(textRenderer, CustomGuiUtils.getFontText("COSMETICS"), px + 34, py + 18, 0xFFFFFFFF, false);

            String[] cosCats = {"ALL", "WINGS", "HEAD", "ACCESSORIES", "PETS", "EMOTES"};
            String[] cosLabels = {"All", "Wings", "Head", "Accessories", "Pets", "Emotes"};
            String[] cosIcons = {"✦", "🪽", "👑", "🐾", "🐉", "💃"};
            int catBtnW = sidebar - 16;
            int catBtnH = 26;
            int startCatY = py + 48;

            for (int i = 0; i < cosCats.length; i++) {
                int by = startCatY + i * (catBtnH + 6);
                boolean active = cosCats[i].equalsIgnoreCase(CosmeticsTab.subCategory);
                boolean hover = inside(mx, my, px + 8, by, catBtnW, catBtnH);

                int btnBg = active ? GuiTheme.alpha(accent, 45) : (hover ? 0xFF1E212D : 0x00000000);
                int btnBorder = active ? accent : (hover ? 0xFF353C4E : 0x00000000);
                if (active || hover) {
                    CustomGuiUtils.fillUltraRounded(c, px + 8, by, catBtnW, catBtnH, btnBg, 4.0f);
                    CustomGuiUtils.drawUltraRoundedOutline(c, px + 8, by, catBtnW, catBtnH, btnBorder, 4.0f);
                }

                String display = cosIcons[i] + " " + cosLabels[i];
                int col = active ? 0xFFFFFFFF : (hover ? 0xFFE2E8F0 : 0xFF8E95A4);
                c.drawText(textRenderer, CustomGuiUtils.getFontText(display), px + 16, by + 8, col, false);
            }

            // Bottom: [← Back]
            int backBtnH = 26;
            int backBtnY = py + ph - 36;
            boolean hoverBack = inside(mx, my, px + 8, backBtnY, catBtnW, backBtnH);
            CustomGuiUtils.fillUltraRounded(c, px + 8, backBtnY, catBtnW, backBtnH, hoverBack ? 0xFF242838 : 0xFF161822, 4.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, px + 8, backBtnY, catBtnW, backBtnH, hoverBack ? accent : 0xFF2A2F40, 4.0f);
            c.drawText(textRenderer, CustomGuiUtils.getFontText("← Back"), px + 18, backBtnY + 8, hoverBack ? 0xFFFFFFFF : 0xFF9CA3AF, false);
        } else {
            int sbBtnW = 58;
            int sbBtnH = 46;
            int sbBtnX = px + 5;

            // Tab 1: [C] MODS
            boolean activeMods = (currentView == ViewMode.MODS);
            boolean hoverMods = inside(mx, my, sbBtnX, py + 10, sbBtnW, sbBtnH);
            if (activeMods || hoverMods) {
                CustomGuiUtils.fillUltraRounded(c, sbBtnX, py + 10, sbBtnW, sbBtnH, activeMods ? GuiTheme.alpha(accent, 38) : 0xFF20232E, 4.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, sbBtnX, py + 10, sbBtnW, sbBtnH, activeMods ? accent : 0xFF3A3F50, 4.0f);
            }
            int modsIconCol = activeMods ? accent : (hoverMods ? 0xFFFFFFFF : 0xFF9CA3AF);
            CustomGuiUtils.drawCLogo(c, sbBtnX + 20, py + 14, 18, modsIconCol);
            int twMods = textRenderer.getWidth(CustomGuiUtils.getFontText("MODS"));
            c.drawText(textRenderer, CustomGuiUtils.getFontText("MODS"), sbBtnX + (sbBtnW - twMods) / 2, py + 38, activeMods ? accent : (hoverMods ? 0xFFFFFFFF : 0xFF8E95A4), false);

            // Tab 2: 👕 COSMETICS
            boolean activeCos = (currentView == ViewMode.COSMETICS && !"SKINS".equalsIgnoreCase(CosmeticsTab.subCategory));
            boolean hoverCos = inside(mx, my, sbBtnX, py + 62, sbBtnW, sbBtnH);
            if (activeCos || hoverCos) {
                CustomGuiUtils.fillUltraRounded(c, sbBtnX, py + 62, sbBtnW, sbBtnH, activeCos ? GuiTheme.alpha(accent, 38) : 0xFF20232E, 4.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, sbBtnX, py + 62, sbBtnW, sbBtnH, activeCos ? accent : 0xFF3A3F50, 4.0f);
            }
            int cosIconCol = activeCos ? accent : (hoverCos ? 0xFFFFFFFF : 0xFF9CA3AF);
            CustomGuiUtils.drawShirtIcon(c, sbBtnX + 20, py + 66, cosIconCol);
            int twCos = textRenderer.getWidth(CustomGuiUtils.getFontText("COSMETICS"));
            c.drawText(textRenderer, CustomGuiUtils.getFontText("COSMETICS"), sbBtnX + (sbBtnW - twCos) / 2, py + 90, activeCos ? accent : (hoverCos ? 0xFFFFFFFF : 0xFF8E95A4), false);

            // Tab 3: 👤 SKINS
            boolean activeSkins = (currentView == ViewMode.COSMETICS && "SKINS".equalsIgnoreCase(CosmeticsTab.subCategory));
            boolean hoverSkins = inside(mx, my, sbBtnX, py + 114, sbBtnW, sbBtnH);
            if (activeSkins || hoverSkins) {
                CustomGuiUtils.fillUltraRounded(c, sbBtnX, py + 114, sbBtnW, sbBtnH, activeSkins ? GuiTheme.alpha(accent, 38) : 0xFF20232E, 4.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, sbBtnX, py + 114, sbBtnW, sbBtnH, activeSkins ? accent : 0xFF3A3F50, 4.0f);
            }
            int skinsIconCol = activeSkins ? accent : (hoverSkins ? 0xFFFFFFFF : 0xFF9CA3AF);
            CustomGuiUtils.drawSkinIcon(c, sbBtnX + 20, py + 118, skinsIconCol);
            int twSkins = textRenderer.getWidth(CustomGuiUtils.getFontText("SKINS"));
            c.drawText(textRenderer, CustomGuiUtils.getFontText("SKINS"), sbBtnX + (sbBtnW - twSkins) / 2, py + 142, activeSkins ? accent : (hoverSkins ? 0xFFFFFFFF : 0xFF8E95A4), false);

            // Tab 4: 📷 GALLERY
            boolean hoverGallery = inside(mx, my, sbBtnX, py + 166, sbBtnW, sbBtnH);
            if (hoverGallery) {
                CustomGuiUtils.fillUltraRounded(c, sbBtnX, py + 166, sbBtnW, sbBtnH, 0xFF20232E, 4.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, sbBtnX, py + 166, sbBtnW, sbBtnH, 0xFF3A3F50, 4.0f);
            }
            int galleryIconCol = hoverGallery ? 0xFFFFFFFF : 0xFF9CA3AF;
            CustomGuiUtils.drawCameraIcon(c, sbBtnX + 20, py + 170, galleryIconCol);
            int twGallery = textRenderer.getWidth(CustomGuiUtils.getFontText("GALLERY"));
            c.drawText(textRenderer, CustomGuiUtils.getFontText("GALLERY"), sbBtnX + (sbBtnW - twGallery) / 2, py + 194, hoverGallery ? 0xFFFFFFFF : 0xFF8E95A4, false);

            // Tab 3 (Bottom): ⌂ HUB
            int hubBtnH = 32;
            int hubBtnY = py + ph - 42;
            boolean hoverHub = inside(mx, my, sbBtnX, hubBtnY, sbBtnW, hubBtnH);
            if (hoverHub) {
                CustomGuiUtils.fillUltraRounded(c, sbBtnX, hubBtnY, sbBtnW, hubBtnH, 0xFF20232E, 4.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, sbBtnX, hubBtnY, sbBtnW, hubBtnH, 0xFF3A3F50, 4.0f);
            }
            int twHub = textRenderer.getWidth(CustomGuiUtils.getFontText("HUB"));
            c.drawText(textRenderer, CustomGuiUtils.getFontText("HUB"), sbBtnX + (sbBtnW - twHub) / 2, hubBtnY + 12, hoverHub ? 0xFFFFFFFF : 0xFF8E95A4, false);
        }

        // Content Area rendering by ViewMode:
        if (currentView == ViewMode.MODS) {
            // Top Category Tabs Header (Combat | Movement | Visuals | World | Misc)
            int tabX = cx + 4;
            for (String cat : CATEGORIES) {
                int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(cat.toUpperCase(java.util.Locale.ROOT)));
                int tabW = tw + 16;
                int tabH = 18;
                int tabY = py + 9;
                boolean active = selectedCategory.equalsIgnoreCase(cat);
                boolean hover = inside(mx, my, tabX, tabY, tabW, tabH);
                if (active) {
                    CustomGuiUtils.fillUltraRounded(c, tabX, tabY, tabW, tabH, GuiTheme.alpha(accent, 38), 3.0f);
                    CustomGuiUtils.drawUltraRoundedOutline(c, tabX, tabY, tabW, tabH, accent, 3.0f);
                    text(c, cat.toUpperCase(java.util.Locale.ROOT), tabX + 8, tabY + 5, accent);
                } else {
                    if (hover) {
                        CustomGuiUtils.fillUltraRounded(c, tabX, tabY, tabW, tabH, 0xFF1C1E28, 3.0f);
                    }
                    text(c, cat.toUpperCase(java.util.Locale.ROOT), tabX + 8, tabY + 5, hover ? 0xFFFFFFFF : 0xFF8E95A4);
                }
                tabX += tabW + 8;
            }

            // Top Search Widget
            int searchBoxX = cx + cw - 128;
            int searchBoxY = py + 8;
            CustomGuiUtils.fillUltraRounded(c, searchBoxX, searchBoxY, 126, 18, 0xFF16171E, 3.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, searchBoxX, searchBoxY, 126, 18, 0xFF292C37, 3.0f);
            CustomGuiUtils.drawSearchIcon(c, searchBoxX + 6, searchBoxY + 5, 0xFF8E95A4);
            search.setX(searchBoxX + 18);
            search.setY(searchBoxY + 1);
            search.setWidth(104);
            search.setHeight(16);
            search.render(c, mx, my, delta);

            // Divider line below top bar
            c.fill(cx, py + 34, cx + cw, py + 35, 0xFF222530);

            // Modules Cards content
            c.enableScissor(cx - 4, cy - 2, cx + cw + 4, py + ph - 20);

            int columnWidth = getColumnWidth();
            int leftX = getContentLeftX();
            int rightX = getContentRightX();
            String q = search.getText().trim();
            ColumnTracker tracker = new ColumnTracker(baseY());

            if (selectedCategory.equalsIgnoreCase("MODS") || !q.isEmpty()) {
                for (Map.Entry<String, ClientTab> entry : tabs.entrySet()) {
                    if (entry.getKey().equals("Cosmetics")) continue;
                    entry.getValue().render(this, c, mx, my, delta, leftX, rightX, columnWidth, tracker, q);
                }
            } else {
                ClientTab activeTab = tabs.get(selectedCategory);
                if (activeTab != null) {
                    activeTab.render(this, c, mx, my, delta, leftX, rightX, columnWidth, tracker, q);
                }
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
            int searchBoxW = 160;
            int searchBoxH = 20;
            CustomGuiUtils.fillUltraRounded(c, searchBoxX, searchBoxY, searchBoxW, searchBoxH, 0xFF141722, 3.5f);
            CustomGuiUtils.drawUltraRoundedOutline(c, searchBoxX, searchBoxY, searchBoxW, searchBoxH, 0xFF282F40, 3.5f);
            CustomGuiUtils.drawSearchIcon(c, searchBoxX + 6, searchBoxY + 6, 0xFF8E95A4);
            search.setX(searchBoxX + 20);
            search.setY(searchBoxY + 1);
            search.setWidth(searchBoxW - 26);
            search.setHeight(18);
            search.render(c, mx, my, delta);

            // Active Category Label
            String catTitle = switch (CosmeticsTab.subCategory) {
                case "WINGS" -> "WINGS";
                case "HEAD" -> "HEAD";
                case "ACCESSORIES" -> "ACCESSORIES";
                case "PETS" -> "PETS";
                case "EMOTES" -> "EMOTES";
                case "SKINS" -> "SKINS";
                default -> "ALL COSMETICS";
            };
            text(c, catTitle, searchBoxX + searchBoxW + 16, py + 14, 0xFF94A3B8);

            // Top-Right: Quick Unequip All Button
            int unequipW = 110;
            int unequipH = 20;
            int unequipX = cx + cw - unequipW;
            int unequipY = py + 8;
            boolean hoverUnequip = inside(mx, my, unequipX, unequipY, unequipW, unequipH);
            CustomGuiUtils.fillUltraRounded(c, unequipX, unequipY, unequipW, unequipH, hoverUnequip ? 0xFF351C24 : 0xFF17151D, 4.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, unequipX, unequipY, unequipW, unequipH, hoverUnequip ? 0xFFF43F5E : 0xFF2D2430, 4.0f);
            int twUnequip = textRenderer.getWidth(CustomGuiUtils.getFontText("✕ Unequip All"));
            text(c, "✕ Unequip All", unequipX + (unequipW - twUnequip) / 2, unequipY + 6, hoverUnequip ? 0xFFFCA5A5 : 0xFFCBD5E1);

            // Divider line below top bar
            c.fill(cx, py + 34, cx + cw, py + 35, 0xFF222530);

            // Content Area (Cards + Preview)
            c.enableScissor(cx - 4, cy - 2, cx + cw + 4, py + ph - 16);
            int columnWidth = getColumnWidth();
            int leftX = getContentLeftX();
            int rightX = getContentRightX();
            String q = search.getText().trim();
            ColumnTracker tracker = new ColumnTracker(baseY());
            ClientTab cosTab = tabs.get("Cosmetics");
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

        } else if (currentView == ViewMode.THEME) {
            c.enableScissor(cx - 4, py + 8, cx + cw + 4, py + ph - 8);
            renderTheme(c, mx, my, delta);
            c.disableScissor();

        } else if (currentView == ViewMode.SETTINGS) {
            c.enableScissor(cx - 4, py + 8, cx + cw + 4, py + ph - 8);
            renderSettings(c, mx, my, delta);
            c.disableScissor();
        }

        if (crosshairColorPickerOpen || hitColorColorPickerOpen || hitboxColorPickerOpen || blockOutlineColorPickerOpen) {
            renderModalColorPicker(c, mx, my);
        }

        if (itemModalOpen) {
            renderItemModal(c, mx, my, delta);
        }

        if (currentView == ViewMode.COSMETICS && CosmeticsTab.customSkinModalOpen) {
            CosmeticsTab.renderCustomSkinModal(this, c, mx, my, delta);
        }

        com.bame.client.notification.NotificationManager.render(c, delta);
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
        if (hitColorColorPickerOpen) {
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

        String title = hitColorColorPickerOpen ? "HIT COLOR" : (hitboxColorPickerOpen ? "HITBOX COLOR" : (blockOutlineColorPickerOpen ? "BLOCK OUTLINE COLOR" : "COLOR PICKER"));
        text(context, title, myCpX + 10, myCpY + 8, 0xFF8E95A4);

        int curCol = hitColorColorPickerOpen ? HitColorModule.color : (hitboxColorPickerOpen ? CustomHitboxesModule.color : (blockOutlineColorPickerOpen ? BlockOutlineModule.color : CustomCrosshairModule.color));
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
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, 0xE8131620, 6.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, 0xFF222634, 6.0f);
        text(c, title, x + 12, y + 8, 0xFFEEEEF2);
        text(c, subtitle, x + 12, y + 22, 0xFF7E8496);
    }

    private void renderSettings(DrawContext c, int mx, int my, float delta) {
        int colW = (cw - 12) / 2;
        int col1X = cx;
        int col2X = cx + colW + 12;
        int cardH = 38;
        int cardGap = 8;
        int curY = baseY() + 4;

        // Section: INTERFACE
        text(c, "INTERFACE", cx + 2, curY + 2, 0xFF7E8496);
        c.fill(cx + 62, curY + 6, cx + cw, curY + 7, 0xFF202430);
        curY += 16;

        // Row 1: Menu Bind | 3D GUI
        renderSettingCard(c, col1X, curY, colW, cardH, "Menu Bind", "Key that opens this menu");
        button(c, keyNameMenuBind(), col1X + colW - 68, curY + 10, 58, 18, mx, my);

        renderSettingCard(c, col2X, curY, colW, cardH, "3D GUI", "Floating panel in the world");
        toggle(c, col2X + colW - 36, curY + 12, BameClientConfig.threeDGui, mx, my, delta);
        curY += cardH + cardGap;

        // Row 2: GUI Animation
        renderSettingCard(c, col1X, curY, colW, cardH, "GUI Animation", "Smooth window open animations");
        button(c, animName(), col1X + colW - 88, curY + 10, 78, 18, mx, my);
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
    }

    private void renderItemModal(DrawContext c, int mx, int my, float delta) {
        int modalW = 350;
        int modalH = 285;
        int modalX = (width - modalW) / 2;
        int modalY = (height - modalH) / 2;

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
        int gridX = modalX + 16;
        int gridY = modalY + 50;
        int gridW = 297;
        int gridH = 185;

        int totalItems = cachedFilteredItems.size();
        int totalRows = (totalItems + 11) / 12;
        int totalContentH = totalRows * 25;
        int maxScroll = Math.max(0, totalContentH - gridH);
        itemModalScroll = Math.clamp(itemModalScroll, 0, maxScroll);

        c.enableScissor(gridX, gridY, gridX + gridW + 2, gridY + gridH);

        Item hoveredItem = null;
        int startRow = (int) (itemModalScroll / 25);
        int endRow = Math.min(totalRows, startRow + (gridH / 25) + 2);

        for (int row = startRow; row < endRow; row++) {
            for (int col = 0; col < 12; col++) {
                int index = row * 12 + col;
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

        int scrollbarX = modalX + modalW - 18;
        int scrollbarW = 4;
        if (maxScroll > 0) {
            box(c, scrollbarX, gridY, scrollbarW, gridH, 0xFF1E212A);
            int thumbH = Math.max(16, (int) ((float) gridH * gridH / totalContentH));
            int thumbY = gridY + (int) ((gridH - thumbH) * (itemModalScroll / maxScroll));
            CustomGuiUtils.fillUltraRounded(c, scrollbarX, thumbY, scrollbarW, thumbH, itemScrollDragging ? 0xFFFFFFFF : GuiTheme.accent(), 2);
        }

        int btnY = modalY + modalH - 34;
        int btnH = 22;
        int btnW = 100;
        int btn1X = modalX + 16;
        int btn2X = btn1X + btnW + 9;
        int btn3X = btn2X + btnW + 9;

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

    private void select(String category) {
        if (!selected.equals(category)) {
            CosmeticsTab.resetPreview();
        }
        itemModalOpen = false;
        themeSettings.close();
        selected = category;
        BameClientConfig.save();
        scroll = 0;
        listeningMenuBind = false;
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
        if (BameClientConfig.threeDGui && leftSkinWidget != null) {
            if (inside(click.x(), click.y(), leftSkinWidget.getX(), leftSkinWidget.getY(), leftSkinWidget.getWidth(), leftSkinWidget.getHeight())) {
                return true;
            }
        }
        double[] localPos = ThreeDGuiManager.screenToLocal(click.x(), click.y());
        Click localClick = (ThreeDGuiManager.isActive())
                ? new Click(localPos[0], localPos[1], click.buttonInfo())
                : click;
        double mx = localClick.x(), my = localClick.y();
        if (localClick.button() != 0) return false;

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
            int modalW = 350;
            int modalH = 285;
            int modalX = (width - modalW) / 2;
            int modalY = (height - modalH) / 2;

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

            int btnY = modalY + modalH - 34;
            int btnH = 22;
            int btnW = 100;
            int btn1X = modalX + 16;
            int btn2X = btn1X + btnW + 9;
            int btn3X = btn2X + btnW + 9;

            if (inside(mx, my, btn1X, btnY, btnW, btnH)) {
                if (itemModalType == 1) {
                    tempToolThresholds.clear();
                } else {
                    tempSelectedItems.clear();
                }
                return true;
            }
            if (inside(mx, my, btn2X, btnY, btnW, btnH)) {
                itemModalOpen = false;
                return true;
            }
            if (inside(mx, my, btn3X, btnY, btnW, btnH)) {
                applyModalSave();
                return true;
            }

            if (cachedFilteredItems == null) updateItemFilter();
            int gridX = modalX + 16;
            int gridY = modalY + 50;
            int gridW = 297;
            int gridH = 185;
            int totalItems = cachedFilteredItems.size();
            int totalRows = (totalItems + 11) / 12;
            int totalContentH = totalRows * 25;
            int maxScroll = Math.max(0, totalContentH - gridH);

            int scrollbarX = modalX + modalW - 18;
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
                if (col >= 0 && col < 12 && (relX % 25) < 22 && (relY % 25) < 22) {
                    int index = row * 12 + col;
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

        if (crosshairColorPickerOpen || hitColorColorPickerOpen || hitboxColorPickerOpen || blockOutlineColorPickerOpen) {
            int CP_W = 150;
            int CP_H = 100;
            int myCpX = (width - CP_W) / 2;
            int myCpY = (height - CP_H) / 2;
            if (inside(mx, my, myCpX + CP_W - 16, myCpY + 4, 14, 14)) {
                crosshairColorPickerOpen = false;
                hitColorColorPickerOpen = false;
                hitboxColorPickerOpen = false;
                blockOutlineColorPickerOpen = false;
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
            cpDrag = -1;
            return true;
        }

        if (currentView == ViewMode.COSMETICS) {
            if (CosmeticsTab.customSkinModalOpen) {
                return CosmeticsTab.handleModalClick(this, localClick, twice);
            }
            // Dedicated Sidebar Categories Clicks
            String[] cosCats = {"ALL", "WINGS", "HEAD", "ACCESSORIES", "PETS", "EMOTES"};
            int catBtnW = sidebar - 16;
            int catBtnH = 26;
            int startCatY = py + 48;
            for (int i = 0; i < cosCats.length; i++) {
                int by = startCatY + i * (catBtnH + 6);
                if (inside(mx, my, px + 8, by, catBtnW, catBtnH)) {
                    CosmeticsTab.subCategory = cosCats[i];
                    scroll = 0;
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
                } else {
                    com.bame.client.cosmetics.CosmeticsConfig.setWings("none");
                    com.bame.client.cosmetics.CosmeticsConfig.setHead("none");
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
            ClientTab cosTab = tabs.get("Cosmetics");
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

        // Left vertical sidebar clicks (sidebar = 68, matching Bild 4)
        int sbBtnW = 58;
        int sbBtnH = 46;
        int sbBtnX = px + 5;
        if (inside(mx, my, sbBtnX, py + 10, sbBtnW, sbBtnH)) {
            if (currentView != ViewMode.MODS) {
                currentView = ViewMode.MODS;
                com.bame.client.sound.ClientSoundManager.playClick();
                geometry();
                layout();
            }
            return true;
        }
        if (inside(mx, my, sbBtnX, py + 62, sbBtnW, sbBtnH)) {
            if (currentView != ViewMode.COSMETICS || "SKINS".equalsIgnoreCase(CosmeticsTab.subCategory)) {
                currentView = ViewMode.COSMETICS;
                CosmeticsTab.subCategory = "ALL";
                com.bame.client.sound.ClientSoundManager.playClick();
                geometry();
                layout();
            }
            return true;
        }
        if (inside(mx, my, sbBtnX, py + 114, sbBtnW, sbBtnH)) {
            if (currentView != ViewMode.COSMETICS || !"SKINS".equalsIgnoreCase(CosmeticsTab.subCategory)) {
                currentView = ViewMode.COSMETICS;
                CosmeticsTab.subCategory = "SKINS";
                com.bame.client.sound.ClientSoundManager.playClick();
                geometry();
                layout();
            }
            return true;
        }
        if (inside(mx, my, sbBtnX, py + 166, sbBtnW, sbBtnH)) {
            com.bame.client.sound.ClientSoundManager.playClick();
            client.setScreen(new GalleryScreen(this));
            return true;
        }
        if (inside(mx, my, sbBtnX, py + ph - 42, sbBtnW, 34)) {
            currentView = ViewMode.HUB;
            com.bame.client.sound.ClientSoundManager.playClick();
            geometry();
            layout();
            return true;
        }

        if (currentView == ViewMode.MODS) {
            // Category tabs
            int tabX = cx + 4;
            for (String cat : CATEGORIES) {
                int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(cat.toUpperCase(java.util.Locale.ROOT)));
                int tabW = tw + 16;
                if (inside(mx, my, tabX, py + 9, tabW, 18)) {
                    selectedCategory = cat;
                    scroll = 0;
                    com.bame.client.sound.ClientSoundManager.playClick();
                    layout();
                    return true;
                }
                tabX += tabW + 8;
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

            if (selectedCategory.equalsIgnoreCase("MODS") || !q.isEmpty()) {
                for (Map.Entry<String, ClientTab> entry : tabs.entrySet()) {
                    if (entry.getKey().equals("Cosmetics")) continue;
                    if (entry.getValue().mouseClicked(this, localClick, twice, leftX, rightX, columnWidth, tracker, q)) {
                        return true;
                    }
                }
            } else {
                ClientTab activeTab = tabs.get(selectedCategory);
                if (activeTab != null) {
                    if (activeTab.mouseClicked(this, localClick, twice, leftX, rightX, columnWidth, tracker, q)) {
                        return true;
                    }
                }
            }
        } else if (currentView == ViewMode.THEME) {
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
        } else if (currentView == ViewMode.SETTINGS) {
            int colW = (cw - 12) / 2;
            int col1X = cx;
            int col2X = cx + colW + 12;
            int cardH = 38;
            int cardGap = 8;
            int curY = baseY() + 4;
            curY += 16;

            if (inside(mx, my, col1X + colW - 68, curY + 10, 58, 18)) {
                listeningMenuBind = true;
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
        if (BameClientConfig.threeDGui && leftSkinWidget != null) {
            if (inside(click.x(), click.y(), leftSkinWidget.getX(), leftSkinWidget.getY(), leftSkinWidget.getWidth(), leftSkinWidget.getHeight())) {
                leftSkinWidget.mouseDragged(click, dx, dy);
                return true;
            }
        }
        double[] localPos = ThreeDGuiManager.screenToLocal(click.x(), click.y());
        Click localClick = (ThreeDGuiManager.isActive())
                ? new Click(localPos[0], localPos[1], click.buttonInfo())
                : click;

        if ((crosshairColorPickerOpen || hitColorColorPickerOpen || hitboxColorPickerOpen || blockOutlineColorPickerOpen) && cpDrag >= 0) {
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
            int modalH = 285;
            int modalY = (height - modalH) / 2;
            int gridY = modalY + 50;
            int gridH = 185;
            if (cachedFilteredItems != null) {
                int totalItems = cachedFilteredItems.size();
                int totalRows = (totalItems + 11) / 12;
                int totalContentH = totalRows * 25;
                int maxScroll = Math.max(0, totalContentH - gridH);
                if (maxScroll > 0) {
                    itemModalScroll = Math.clamp((localClick.y() - gridY) / (double) gridH * maxScroll, 0, maxScroll);
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
            ClientTab cosTab = tabs.get("Cosmetics");
            if (cosTab != null && cosTab.mouseDragged(this, localClick, dx, dy)) {
                return true;
            }
        } else {
            String q = search.getText().trim();
            if (q.isEmpty()) {
                ClientTab activeTab = tabs.get(selected);
                if (activeTab != null && activeTab.mouseDragged(this, localClick, dx, dy)) {
                    return true;
                }
            } else {
                for (ClientTab tab : tabs.values()) {
                    if (tab.mouseDragged(this, localClick, dx, dy)) {
                        return true;
                    }
                }
            }
        }

        return super.mouseDragged(localClick, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double[] localPos = ThreeDGuiManager.screenToLocal(click.x(), click.y());
        Click localClick = (ThreeDGuiManager.isActive())
                ? new Click(localPos[0], localPos[1], click.buttonInfo())
                : click;

        cpDrag = -1;
        itemScrollDragging = false;
        boolean handled = scrollDragging || themeSettings.dragging() || draggingSoundVolume || draggingHoverVolume;
        scrollDragging = false;
        draggingSoundVolume = false;
        draggingHoverVolume = false;

        for (ClientTab tab : tabs.values()) {
            if (tab.mouseReleased(this, localClick)) {
                handled = true;
            }
        }

        BameClientConfig.save();
        themeSettings.release();
        return handled || super.mouseReleased(localClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        double[] localPos = ThreeDGuiManager.screenToLocal(mx, my);
        double lmx = localPos[0], lmy = localPos[1];

        if (itemModalOpen) {
            int modalW = 350;
            int modalH = 285;
            int modalX = (width - modalW) / 2;
            int modalY = (height - modalH) / 2;
            if (inside(lmx, lmy, modalX, modalY, modalW, modalH)) {
                if (cachedFilteredItems != null) {
                    int totalRows = (cachedFilteredItems.size() + 11) / 12;
                    int totalContentH = totalRows * 25;
                    int maxScroll = Math.max(0, totalContentH - 185);
                    if (maxScroll > 0) {
                        itemModalScroll = Math.clamp(itemModalScroll - vertical * 25, 0, maxScroll);
                    }
                }
                return true;
            }
            return true;
        }
        if (inside(lmx, lmy, cx, cy, cw + 14, ch) && !themeSettings.dragging()) {
            ClientTab currentTab = (currentView == ViewMode.COSMETICS) ? tabs.get("Cosmetics") : tabs.get(selected);
            if (currentTab != null && currentTab.mouseScrolled(lmx, lmy, horizontal, vertical)) {
                return true;
            }
            scroll = Math.clamp(scroll - vertical * 26, 0, maxScroll());
            layout();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (currentView == ViewMode.COSMETICS && CosmeticsTab.customSkinModalOpen) {
            if (CosmeticsTab.handleModalKeyPressed(this, input)) {
                return true;
            }
        }
        if (itemModalOpen) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                applyModalSave();
                return true;
            }
            if (itemSearchWidget != null && itemSearchWidget.isFocused()) {
                if (itemSearchWidget.keyPressed(input)) return true;
            }
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE && (crosshairColorPickerOpen || hitColorColorPickerOpen || hitboxColorPickerOpen || blockOutlineColorPickerOpen)) {
            crosshairColorPickerOpen = false;
            hitColorColorPickerOpen = false;
            hitboxColorPickerOpen = false;
            blockOutlineColorPickerOpen = false;
            cpDrag = -1;
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE && !listeningMenuBind) {
            themeSettings.close();
            if (currentView != ViewMode.HUB) {
                currentView = ViewMode.HUB;
                layout();
                return true;
            }
        }
        if (listeningMenuBind) {
            BameClientConfig.menuBind = input.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : input.key();
            listeningMenuBind = false;
            BameClientConfig.save();
            return true;
        }
        if (isSecretComboPressed(input)) {
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
        themeSettings.close();
        BameClientConfig.save();
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
        BameClientConfig.save();
    }
}
