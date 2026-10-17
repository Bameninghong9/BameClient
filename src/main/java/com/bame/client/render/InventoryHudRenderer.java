package com.bame.client.render;

import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.module.InventoryHudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Inventar-Vorschau („Inventory HUD“): die 27 Slots des Hauptinventars (3x9), optional mit Hotbar-Reihe, mit Items,
 * Anzahl, Haltbarkeitsbalken und Abklingzeit wie Vanilla ({@code drawStackOverlay}). Zwei Slot-Stile: Hotbar-Look
 * (Vanilla-Sprite {@code hud/hotbar}, eine Reihe je Zeile, folgt Ressourcenpaketen) oder nur Items. Hintergrund-Modi,
 * Größe und Position wie die anderen HUD-Elemente; Position frei mit Begrenzung am Rand ({@link InventoryHudLayout}).
 *
 * <p>Leistung: keine Allokation pro Bild im eigenen Code (Maße in einem wiederverwendeten {@link InventoryHudLayout.Metrics},
 * Items direkt aus dem Inventar, Platzhalter einmal angelegt). Nur Render-Thread. Profiler-Abschnitt
 * {@code caeser_hud_inventory} (in {@link StatusHudRenderer}).
 */
public final class InventoryHudRenderer {
    private InventoryHudRenderer() {}

    private static final Identifier HOTBAR = Identifier.ofVanilla("hud/hotbar");
    private static final int HOTBAR_SPRITE_W = 182;
    private static final int HOTBAR_SPRITE_H = 22;
    private static final Identifier TOOLTIP_BG = Identifier.ofVanilla("tooltip/background");
    private static final Identifier TOOLTIP_FRAME = Identifier.ofVanilla("tooltip/frame");

    /** Platzhalter für die Vorschau im HUD-Editor, falls das Inventar leer ist (einmal angelegt; Index = Inventar-Slot). */
    private static final class Preview {
        static final ItemStack[] INV = new ItemStack[InventoryHudLayout.MAX_SLOTS];
        static {
            INV[9] = new ItemStack(Items.ENDER_PEARL, 16);
            INV[10] = new ItemStack(Items.GOLDEN_APPLE, 16);
            INV[11] = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 3);
            INV[12] = new ItemStack(Items.SPLASH_POTION);
            INV[13] = new ItemStack(Items.SPLASH_POTION);
            INV[14] = new ItemStack(Items.POTION);
            INV[15] = new ItemStack(Items.POTION);
            INV[16] = worn(Items.NETHERITE_PICKAXE, 0.62f);
            INV[17] = worn(Items.NETHERITE_AXE, 0.35f);
            INV[18] = worn(Items.NETHERITE_SHOVEL, 0.9f);
            INV[19] = new ItemStack(Items.ARROW, 64);
            INV[20] = new ItemStack(Items.WIND_CHARGE, 8);
            INV[21] = new ItemStack(Items.COBWEB, 16);
            INV[22] = new ItemStack(Items.OBSIDIAN, 32);
            INV[23] = new ItemStack(Items.WATER_BUCKET);
            INV[24] = new ItemStack(Items.TOTEM_OF_UNDYING);
            INV[25] = new ItemStack(Items.EXPERIENCE_BOTTLE, 64);
            INV[26] = new ItemStack(Items.GOLDEN_CARROT, 64);
            INV[27] = new ItemStack(Items.FIREWORK_ROCKET, 32);
            INV[28] = new ItemStack(Items.RESPAWN_ANCHOR, 4);
            INV[29] = new ItemStack(Items.GLOWSTONE, 32);
            INV[30] = new ItemStack(Items.SPLASH_POTION);
            INV[31] = new ItemStack(Items.ANVIL);
            INV[32] = new ItemStack(Items.CRAFTING_TABLE);
            INV[33] = new ItemStack(Items.TOTEM_OF_UNDYING);
            INV[34] = worn(Items.CROSSBOW, 0.8f);
            INV[35] = new ItemStack(Items.TOTEM_OF_UNDYING);
            INV[0] = worn(Items.NETHERITE_SWORD, 0.93f);
            INV[1] = new ItemStack(Items.GOLDEN_APPLE, 16);
            INV[2] = new ItemStack(Items.ENDER_PEARL, 16);
            INV[3] = new ItemStack(Items.SPLASH_POTION);
            INV[4] = worn(Items.BOW, 0.5f);
            INV[5] = new ItemStack(Items.WIND_CHARGE, 4);
            INV[6] = new ItemStack(Items.COBWEB, 8);
            INV[7] = worn(Items.SHIELD, 0.28f);
            INV[8] = new ItemStack(Items.TOTEM_OF_UNDYING);
        }

        private static ItemStack worn(Item item, float left) {
            ItemStack stack = new ItemStack(item);
            if (stack.isDamageable()) stack.setDamage(Math.round(stack.getMaxDamage() * (1.0f - left)));
            return stack;
        }
    }

    private static final InventoryHudLayout.Metrics METRICS = new InventoryHudLayout.Metrics();
    /** Dieses Bild mit Platzhaltern (Editor-Vorschau mit leerem Inventar)? */
    private static boolean placeholders;
    private static int boxW;
    private static int boxH;
    private static int contentDX;
    private static int contentDY;

    // Ergebnis von place()
    private static int placedX;
    private static int placedY;
    private static float placedScale = 1.0f;

    // ------------------------------------------------------------------------------------------------------------
    // Öffentliche Schnittstelle
    // ------------------------------------------------------------------------------------------------------------

    /** Im Spiel (StatusHudRenderer, Profiler-Abschnitt {@code caeser_hud_inventory}). */
    public static void renderInGame(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (InventoryHudModule.hideWhenInventoryOpen && client.currentScreen instanceof HandledScreen<?>) return;
        place(context.getScaledWindowWidth(), context.getScaledWindowHeight(), false);
        draw(context, placedX, placedY, placedScale);
    }

    /** Zeichnet an einer vorgegebenen Stelle (HUD-Editor). {@code preview}: Platzhalter, falls das Inventar leer ist. */
    public static void render(DrawContext context, int x, int y, float scale, boolean preview) {
        prepare(preview);
        draw(context, x, y, InventoryHudLayout.sanitizeScale(scale));
    }

    /**
     * Maße und Position für diesen Bildschirm (gespeicherte Position oder automatisch unten rechts, immer ganz im Bild).
     * Ergebnis über {@link #placedX()}, {@link #placedY()}, {@link #placedScale()}, {@link #boxWidth()},
     * {@link #boxHeight()}.
     */
    public static void place(int screenW, int screenH, boolean preview) {
        prepare(preview);
        float s = InventoryHudLayout.sanitizeScale(InventoryHudModule.scale);
        placedX = InventoryHudLayout.freeX(InventoryHudModule.hudX, screenW, InventoryHudLayout.scaledSize(boxW, s));
        placedY = InventoryHudLayout.freeY(InventoryHudModule.hudY, screenH, InventoryHudLayout.scaledSize(boxH, s));
        placedScale = s;
    }

    public static int placedX() {
        return placedX;
    }

    public static int placedY() {
        return placedY;
    }

    public static float placedScale() {
        return placedScale;
    }

    /** Breite des Elements (unskaliert, mindestens die eigene Breite aus dem HUD-Editor). */
    public static int boxWidth() {
        return boxW;
    }

    public static int boxHeight() {
        return boxH;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Messen und Zeichnen
    // ------------------------------------------------------------------------------------------------------------

    private static void prepare(boolean preview) {
        boolean bgVisible = InventoryHudModule.bgMode != 0;
        InventoryHudLayout.Metrics m = METRICS.compute(InventoryHudModule.slotStyle, InventoryHudModule.includeHotbar, bgVisible);
        PlayerEntity player = MinecraftClient.getInstance().player;
        placeholders = preview && (player == null || isEmpty(player.getInventory(), m.hotbar));
        int naturalW = m.width;
        int naturalH = m.height;
        boxW = InventoryHudModule.customWidth > 0 ? Math.max(naturalW, InventoryHudModule.customWidth) : naturalW;
        boxH = InventoryHudModule.customHeight > 0 ? Math.max(naturalH, InventoryHudModule.customHeight) : naturalH;
        contentDX = (boxW - naturalW) / 2;
        contentDY = (boxH - naturalH) / 2;
    }

    private static boolean isEmpty(PlayerInventory inventory, boolean hotbar) {
        int rows = InventoryHudLayout.rows(hotbar);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < InventoryHudLayout.COLUMNS; col++) {
                if (!inventory.getStack(InventoryHudLayout.slotIndex(row, col)).isEmpty()) return false;
            }
        }
        return true;
    }

    private static void draw(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null && !placeholders) return;
        InventoryHudLayout.Metrics m = METRICS;
        float opacity = InventoryHudLayout.sanitizeOpacity(InventoryHudModule.opacity);
        TextRenderer textRenderer = client.textRenderer;
        Matrix3x2fStack matrices = c.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate((float) x, (float) y);
            matrices.scale(scale, scale);
            drawBackground(c, boxW, boxH, InventoryHudModule.bgMode, InventoryHudModule.outlineColor, opacity);
            if (contentDX != 0 || contentDY != 0) matrices.translate((float) contentDX, (float) contentDY);

            if (m.style == InventoryHudLayout.STYLE_HOTBAR) {
                int color = InventoryHudLayout.applyOpacity(0xFFFFFFFF, opacity);
                drawHotbarRows(c, m.gridX, m.gridY, InventoryHudLayout.MAIN_ROWS, color);
                if (m.hotbar) drawHotbarRows(c, m.gridX, m.hotbarY, 1, color);
            }

            PlayerInventory inventory = player != null ? player.getInventory() : null;
            int rows = m.rows();
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < InventoryHudLayout.COLUMNS; col++) {
                    int slot = InventoryHudLayout.slotIndex(row, col);
                    ItemStack stack = placeholders ? Preview.INV[slot] : inventory.getStack(slot);
                    if (stack == null || stack.isEmpty()) continue;
                    int ix = m.itemX(col);
                    int iy = m.itemY(row);
                    c.drawItem(stack, ix, iy);
                    c.drawStackOverlay(textRenderer, stack, ix, iy);
                }
            }
        } finally {
            matrices.popMatrix();
        }
    }

    /**
     * Vanilla-Hotbar-Sprite je Zeile. Die Zeilen liegen im 20-px-Raster und teilen sich den Rand: jede Zeile außer der
     * letzten nur mit den oberen 20 Pixeln, damit sich nichts überdeckt (bei verringerter Deckkraft sonst dunklere Linien).
     */
    private static void drawHotbarRows(DrawContext c, int x, int y, int rows, int color) {
        for (int r = 0; r < rows; r++) {
            int h = r == rows - 1 ? HOTBAR_SPRITE_H : InventoryHudLayout.H_CELL;
            c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_SPRITE_W, HOTBAR_SPRITE_H, 0, 0,
                    x, y + r * InventoryHudLayout.H_CELL, HOTBAR_SPRITE_W, h, color);
        }
    }

    /** Hintergrund-Modi wie {@code StatusHudRenderer.drawBoxBg}, mit Deckkraft. */
    private static void drawBackground(DrawContext c, int w, int h, int bgMode, int outlineColor, float opacity) {
        switch (bgMode) {
            case 1 -> { // Tooltip (9 px nach außen, damit der sichtbare Rahmen genau auf x, y, w, h liegt)
                int color = InventoryHudLayout.applyOpacity(0xFFFFFFFF, opacity);
                c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, TOOLTIP_BG, -9, -9, w + 18, h + 18, color);
                c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, TOOLTIP_FRAME, -9, -9, w + 18, h + 18, color);
            }
            case 2 -> { // Dark
                CustomGuiUtils.fillUltraRounded(c, 0, 0, w, h, InventoryHudLayout.applyOpacity(0x880D1117, opacity), 4);
                CustomGuiUtils.drawUltraRoundedOutline(c, 0, 0, w, h, InventoryHudLayout.applyOpacity(0x28FFFFFF, opacity), 4);
            }
            case 3 -> CustomGuiUtils.drawUltraRoundedOutline(c, 0, 0, w, h, InventoryHudLayout.applyOpacity(outlineColor, opacity), 4);
            default -> {
                // Transparent
            }
        }
    }
}
