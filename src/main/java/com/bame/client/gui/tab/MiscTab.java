package com.bame.client.gui.tab;

import com.bame.client.BameClientConfig;
import com.bame.client.gui.BameClientScreen;
import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.gui.CustomTextFieldWidget;
import com.bame.client.gui.GuiTheme;
import com.bame.client.module.AutoToolModule;
import com.bame.client.module.ContainerSearchModule;
import com.bame.client.module.DurabilityGuardModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
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
import java.util.Locale;
import java.util.Map;

public class MiscTab implements ClientTab {
    private boolean listeningAutoTool = false;
    private boolean listeningDurabilityGuard = false;
    private final Map<String, CustomTextFieldWidget> toolThresholdFields = new LinkedHashMap<>();

    @Override
    public String getCategory() {
        return "Misc";
    }

    @Override
    public boolean isListeningForKey() {
        return listeningAutoTool || listeningDurabilityGuard;
    }

    @Override
    public void cancelKeyListening() {
        clearKeyListeners();
    }

    @Override
    public boolean hasFocusedTextField() {
        for (CustomTextFieldWidget field : toolThresholdFields.values()) {
            if (field.isFocused()) return true;
        }
        return false;
    }

    public boolean autoToolVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "autotool".contains(q) || "auto tool".contains(q) || "tool".contains(q) || "werkzeug".contains(q);
        return true;
    }

    public boolean durabilityGuardVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "durabilityguard".contains(q) || "durability guard".contains(q) || "durability".contains(q) || "guard".contains(q) || "haltbarkeit".contains(q);
        return true;
    }

    public boolean containerSearchVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "container search".contains(q) || "containersearch".contains(q) || "chest search".contains(q)
                || "search".contains(q) || "suche".contains(q) || "truhe".contains(q) || "kiste".contains(q)
                || "shulker".contains(q) || "inventory".contains(q) || "inventar".contains(q);
        return true;
    }

    @Override
    public boolean isVisible(String search) {
        if (search.isEmpty()) return true;
        return autoToolVisible(search) || durabilityGuardVisible(search) || containerSearchVisible(search);
    }

    public int getAutoToolHeight() {
        return AutoToolModule.expanded ? 130 : 50;
    }

    /** Kopf 54 + fünf Schalter-Zeilen à 26 + Reset-Zeile (16) + 6 Rand. */
    public int getContainerSearchHeight() {
        return ContainerSearchModule.expanded ? 206 : 50;
    }

    public int getDurabilityGuardHeight() {
        if (!DurabilityGuardModule.expanded) return 50;
        return 172 + (DurabilityGuardModule.toolThresholds.size() * 24);
    }

    @Override
    public void layout(BameClientScreen screen, int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
    }

    @Override
    public void calculateHeight(BameClientScreen screen, int columnWidth, ColumnTracker tracker, String search) {
        if (autoToolVisible(search)) tracker.leftY += getAutoToolHeight() + 12;
        if (containerSearchVisible(search)) tracker.leftY += getContainerSearchHeight() + 12;
        if (durabilityGuardVisible(search)) tracker.rightY += getDurabilityGuardHeight() + 12;
    }

    @Override
    public void render(BameClientScreen screen, DrawContext c, int mx, int my, float delta,
                       int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        if (autoToolVisible(search)) {
            renderAutoTool(screen, c, mx, my, delta, leftX, tracker.leftY, columnWidth);
            tracker.leftY += getAutoToolHeight() + 12;
        }
        if (containerSearchVisible(search)) {
            renderContainerSearch(screen, c, mx, my, delta, leftX, tracker.leftY, columnWidth);
            tracker.leftY += getContainerSearchHeight() + 12;
        }
        if (durabilityGuardVisible(search)) {
            renderDurabilityGuard(screen, c, mx, my, delta, rightX, tracker.rightY, columnWidth);
            tracker.rightY += getDurabilityGuardHeight() + 12;
        }
    }

    private void renderAutoTool(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getAutoToolHeight();
        screen.moduleCard(c, x, y, w, h, AutoToolModule.enabled, AutoToolModule.expanded,
                "Auto Tool", "Automatically switches to best tool", null, "misc", mx, my);

        if (!AutoToolModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        screen.keybindButton(c, "AutoToolModule", AutoToolModule.keyBind, listeningAutoTool, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Switch Back", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, AutoToolModule.switchBack, mx, my, delta);
        curY += 26;

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    // Container Search: Zeichnen und Klicks nutzen dieselbe Zeilenfolge (Schalter rechts, 26 px pro Zeile)
    private void renderContainerSearch(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getContainerSearchHeight();
        screen.moduleCard(c, x, y, w, h, ContainerSearchModule.enabled, ContainerSearchModule.expanded,
                "Container Search", "Search bar below chests, grays out other items", null, "misc", mx, my);

        if (!ContainerSearchModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "Also in player inventory", x + 14, curY + 5, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, ContainerSearchModule.playerInventory, mx, my, delta);
        curY += 26;

        screen.text(c, "Search lore & enchantments", x + 14, curY + 5, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, ContainerSearchModule.searchLoreEnchantments, mx, my, delta);
        curY += 26;

        screen.text(c, "Search inside shulkers", x + 14, curY + 5, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, ContainerSearchModule.searchShulkers, mx, my, delta);
        curY += 26;

        screen.text(c, "Highlight matches", x + 14, curY + 5, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, ContainerSearchModule.highlightMatches, mx, my, delta);
        curY += 26;

        screen.text(c, "Remember search", x + 14, curY + 5, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, ContainerSearchModule.rememberSearch, mx, my, delta);
        curY += 26;

        screen.text(c, "Ctrl+F: focus search", x + 14, curY + 4, 0xFF8E95A4);
        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private boolean clickContainerSearch(BameClientScreen screen, double mx, double my, int x, int y, int w) {
        int curY = y + 54;
        if (screen.inside(mx, my, x + w - 38, curY + 3, 26, 14)) {
            ContainerSearchModule.playerInventory = !ContainerSearchModule.playerInventory;
            BameClientConfig.save();
            return true;
        }
        curY += 26;

        if (screen.inside(mx, my, x + w - 38, curY + 3, 26, 14)) {
            ContainerSearchModule.searchLoreEnchantments = !ContainerSearchModule.searchLoreEnchantments;
            BameClientConfig.save();
            return true;
        }
        curY += 26;

        if (screen.inside(mx, my, x + w - 38, curY + 3, 26, 14)) {
            ContainerSearchModule.searchShulkers = !ContainerSearchModule.searchShulkers;
            BameClientConfig.save();
            return true;
        }
        curY += 26;

        if (screen.inside(mx, my, x + w - 38, curY + 3, 26, 14)) {
            ContainerSearchModule.highlightMatches = !ContainerSearchModule.highlightMatches;
            BameClientConfig.save();
            return true;
        }
        curY += 26;

        if (screen.inside(mx, my, x + w - 38, curY + 3, 26, 14)) {
            ContainerSearchModule.rememberSearch = !ContainerSearchModule.rememberSearch;
            BameClientConfig.save();
            return true;
        }
        curY += 26;

        if (screen.inside(mx, my, x + w - 58, curY, 46, 16)) {
            ContainerSearchModule.resetOptions();
            BameClientConfig.save();
            return true;
        }
        return false;
    }

    private void renderDurabilityGuard(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getDurabilityGuardHeight();
        screen.moduleCard(c, x, y, w, h, DurabilityGuardModule.enabled, DurabilityGuardModule.expanded,
                "Durability Guard", "Alerts before item durability breaks", null, "misc", mx, my);

        if (!DurabilityGuardModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        screen.keybindButton(c, "DurabilityGuardModule", DurabilityGuardModule.keyBind, listeningDurabilityGuard, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Alert Type", x + 14, curY + 4, 0xFFD4D8E0);
        String alertStr = DurabilityGuardModule.alertMode == 0 ? "Subtitle" : (DurabilityGuardModule.alertMode == 1 ? "ActionBar" : "Both");
        screen.button(c, alertStr, x + w - 74, curY + 2, 60, 16, mx, my);
        curY += 26;

        screen.text(c, "Tools", x + 14, curY, 0xFFABB1BE);
        int boxX = x + 14;
        int boxY = curY + 12;
        int boxW = w - 28;
        int boxH = 22;
        boolean boxHovered = screen.inside(mx, my, boxX, boxY, boxW, boxH);
        CustomGuiUtils.fillUltraRounded(c, boxX, boxY, boxW, boxH, boxHovered ? 0xFF181C26 : 0xFF12151D, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, boxX, boxY, boxW, boxH, boxHovered ? GuiTheme.accent() : 0xFF2A2E3D, 4);

        int count = DurabilityGuardModule.toolThresholds.size();
        String countText = count > 0 ? count + " tool(s) configured" : "Click to select tools";
        screen.text(c, countText, boxX + 8, boxY + 7, 0xFFE2E5ED);

        curY += 12 + 22 + 8;

        for (Map.Entry<String, Integer> entry : DurabilityGuardModule.toolThresholds.entrySet()) {
            String toolId = entry.getKey();
            int threshold = entry.getValue();

            Item item = null;
            try {
                item = Registries.ITEM.get(Identifier.of(toolId));
            } catch (Exception ignored) {}

            ItemStack stack = (item != null && item != Items.AIR) ? new ItemStack(item) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                c.drawItem(stack, x + 14, curY + 4);
            }

            String toolName = (!stack.isEmpty()) ? stack.getName().getString() : toolId;
            c.enableScissor(x + 36, curY, x + w - 86, curY + 24);
            screen.text(c, toolName, x + 36, curY + 7, 0xFFE2E5ED);
            c.disableScissor();

            int fieldW = 42;
            int fieldH = 16;
            int fieldX = x + w - 38 - fieldW;
            int fieldY = curY + 3;

            CustomTextFieldWidget field = toolThresholdFields.get(toolId);
            if (field == null) {
                field = new CustomTextFieldWidget(fieldX, fieldY, fieldW, fieldH, Text.literal("Threshold"));
                field.setPlaceholder("10");
                field.setText(String.valueOf(threshold));
                String captureId = toolId;
                field.setChangedListener(val -> {
                    try {
                        int parsed = Integer.parseInt(val.trim());
                        if (parsed > 0) {
                            DurabilityGuardModule.toolThresholds.put(captureId, parsed);
                            BameClientConfig.save();
                        }
                    } catch (NumberFormatException ignored) {}
                });
                toolThresholdFields.put(toolId, field);
            } else {
                field.setX(fieldX);
                field.setY(fieldY);
                field.setWidth(fieldW);
                field.setHeight(fieldH);
                if (!field.isFocused() && !field.getText().equals(String.valueOf(threshold))) {
                    field.setText(String.valueOf(threshold));
                }
            }
            field.render(c, mx, my, delta);

            int trashX = x + w - 32;
            int trashY = curY + 3;
            boolean trashHover = screen.inside(mx, my, trashX, trashY, 16, 16);
            CustomGuiUtils.fillUltraRounded(c, trashX, trashY, 16, 16, trashHover ? 0xFF3A1C20 : 0xFF181C24, 3);
            CustomGuiUtils.drawUltraRoundedOutline(c, trashX, trashY, 16, 16, trashHover ? 0xFFE55757 : 0xFF292D36, 3);
            CustomGuiUtils.drawTrashIcon(c, trashX, trashY, trashHover ? 0xFFE55757 : 0xFF8E95A4);

            curY += 24;
        }

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    @Override
    public boolean mouseClicked(BameClientScreen screen, Click click, boolean twice,
                                int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        double mx = click.x();
        double my = click.y();

        if (autoToolVisible(search)) {
            int y = tracker.leftY;
            int h = getAutoToolHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, leftX, y, columnWidth,
                    () -> {
                        AutoToolModule.enabled = !AutoToolModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Auto Tool", AutoToolModule.enabled);
                    },
                    () -> AutoToolModule.expanded = !AutoToolModule.expanded)) {
                return true;
            }
            if (AutoToolModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, leftX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningAutoTool = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 2, 26, 14)) {
                    AutoToolModule.switchBack = !AutoToolModule.switchBack;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;
                if (screen.inside(mx, my, leftX + columnWidth - 58, curY, 46, 16)) {
                    AutoToolModule.resetToDefault();
                    BameClientConfig.save();
                    return true;
                }
            }
            tracker.leftY += h + 12;
        }

        if (containerSearchVisible(search)) {
            int y = tracker.leftY;
            int h = getContainerSearchHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, leftX, y, columnWidth,
                    () -> {
                        ContainerSearchModule.enabled = !ContainerSearchModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Container Search", ContainerSearchModule.enabled);
                    },
                    () -> ContainerSearchModule.expanded = !ContainerSearchModule.expanded)) {
                return true;
            }
            if (ContainerSearchModule.expanded && clickContainerSearch(screen, mx, my, leftX, y, columnWidth)) return true;
            tracker.leftY += h + 12;
        }

        if (durabilityGuardVisible(search)) {
            int y = tracker.rightY;
            int h = getDurabilityGuardHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, rightX, y, columnWidth,
                    () -> {
                        DurabilityGuardModule.enabled = !DurabilityGuardModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Durability Guard", DurabilityGuardModule.enabled);
                    },
                    () -> DurabilityGuardModule.expanded = !DurabilityGuardModule.expanded)) {
                return true;
            }
            if (DurabilityGuardModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, rightX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningDurabilityGuard = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 74, curY + 2, 60, 16)) {
                    DurabilityGuardModule.alertMode = (DurabilityGuardModule.alertMode + 1) % 3;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                int boxX = rightX + 14;
                int boxY = curY + 12;
                int boxW = columnWidth - 28;
                int boxH = 22;
                if (screen.inside(mx, my, boxX, boxY, boxW, boxH)) {
                    screen.openToolModal();
                    return true;
                }
                curY += 12 + 22 + 8;

                String toRemove = null;
                for (String toolId : DurabilityGuardModule.toolThresholds.keySet()) {
                    CustomTextFieldWidget field = toolThresholdFields.get(toolId);
                    if (field != null && field.mouseClicked(click, twice)) {
                        field.setFocused(true);
                        return true;
                    }
                    int trashX = rightX + columnWidth - 32;
                    int trashY = curY + 3;
                    if (screen.inside(mx, my, trashX, trashY, 16, 16)) {
                        toRemove = toolId;
                    }
                    curY += 24;
                }
                if (toRemove != null) {
                    DurabilityGuardModule.toolThresholds.remove(toRemove);
                    toolThresholdFields.remove(toRemove);
                    BameClientConfig.save();
                    return true;
                }

                if (screen.inside(mx, my, rightX + columnWidth - 58, curY, 46, 16)) {
                    DurabilityGuardModule.resetToDefault();
                    toolThresholdFields.clear();
                    BameClientConfig.save();
                    return true;
                }
            }
            tracker.rightY += h + 12;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(BameClientScreen screen, Click click, double deltaX, double deltaY) {
        return false;
    }

    @Override
    public boolean mouseReleased(BameClientScreen screen, Click click) {
        return false;
    }

    @Override
    public boolean keyPressed(BameClientScreen screen, KeyInput input) {
        int key = input.key();
        if (listeningAutoTool) {
            AutoToolModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningAutoTool = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningDurabilityGuard) {
            DurabilityGuardModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningDurabilityGuard = false;
            BameClientConfig.save();
            return true;
        }
        for (CustomTextFieldWidget field : toolThresholdFields.values()) {
            if (field.isFocused() && field.keyPressed(input)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(BameClientScreen screen, CharInput input) {
        for (CustomTextFieldWidget field : toolThresholdFields.values()) {
            if (field.isFocused() && field.charTyped(input)) {
                return true;
            }
        }
        return false;
    }

    private void clearKeyListeners() {
        listeningAutoTool = false;
        listeningDurabilityGuard = false;
    }
}
