package com.bame.client.render;

import com.bame.client.BameClient;
import com.bame.client.module.HotbarModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.bar.Bar;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.GameMode;
import org.joml.Matrix3x2fStack;

import java.util.function.Function;

/**
 * Verschiebbare Vanilla-Hotbar (HUD-Editor, Element „Hotbar“).
 *
 * <p>Die Vanilla-HUD-Elemente werden über {@link HudElementRegistry#replaceElement} nur umhüllt: vor dem Zeichnen wird
 * die GUI-Matrix verschoben (und skaliert), danach in {@code finally} zurückgesetzt. Nichts wird entfernt oder doppelt
 * gezeichnet; Ersetzungen anderer Mods bleiben in der Kette (Fabric verkettet die Ersetzungsfunktionen).
 *
 * <p>Gruppe Hotbar (wandert immer): Hotbar mit Auswahlrahmen, Offhand-Feld und Angriffsanzeige im Hotbar-Modus
 * ({@code HOTBAR}), Erfahrungs-/Locator-/Sprungleiste ({@code INFO_BAR}, Wegpunkt-Symbole der Locator-Leiste über
 * {@code InGameHudMixin} und {@link #wrapBarForAddons}) und Level-Zahl ({@code EXPERIENCE_LEVEL}).
 * Gruppe Statusleisten (nur mit {@link HotbarModule#moveStatusBars}): {@code ARMOR_BAR}, {@code HEALTH_BAR},
 * {@code FOOD_BAR}, {@code AIR_BAR}, {@code MOUNT_HEALTH} und der Item-Name über der Hotbar ({@code HELD_ITEM_TOOLTIP}).
 * Beide Gruppen nutzen pro Frame dieselbe Umrechnung, damit nichts auseinanderfällt.
 *
 * <p>Nicht verschoben: F1 (Vanilla zeichnet die Elemente dann gar nicht), Zuschauermodus (eigene Leiste, die von unten
 * einfährt; alles bleibt an der Vanilla-Stelle) und die Hotbar-Zeile im Inventar (gehört zum Inventar-Bildschirm,
 * nicht zum HUD).
 */
public final class HotbarHud {
    private HotbarHud() {}

    /** Profiler-Abschnitt (F3+L) um die Matrix-Arbeit; das Zeichnen von Vanilla selbst liegt außerhalb. */
    public static final String PROFILER_SECTION = "caeser_hud_hotbar_offset";

    private static final Identifier HOTBAR = Identifier.ofVanilla("hud/hotbar");
    private static final Identifier HOTBAR_SELECTION = Identifier.ofVanilla("hud/hotbar_selection");
    private static final Identifier HOTBAR_OFFHAND_LEFT = Identifier.ofVanilla("hud/hotbar_offhand_left");
    private static final Identifier HOTBAR_OFFHAND_RIGHT = Identifier.ofVanilla("hud/hotbar_offhand_right");
    private static final Identifier XP_BACKGROUND = Identifier.ofVanilla("hud/experience_bar_background");
    private static final Identifier XP_PROGRESS = Identifier.ofVanilla("hud/experience_bar_progress");
    private static final Identifier HEART_CONTAINER = Identifier.ofVanilla("hud/heart/container");
    private static final Identifier HEART_FULL = Identifier.ofVanilla("hud/heart/full");
    private static final Identifier HEART_HALF = Identifier.ofVanilla("hud/heart/half");
    private static final Identifier FOOD_EMPTY = Identifier.ofVanilla("hud/food_empty");
    private static final Identifier FOOD_HALF = Identifier.ofVanilla("hud/food_half");
    private static final Identifier FOOD_FULL = Identifier.ofVanilla("hud/food_full");
    private static final Identifier ARMOR_EMPTY = Identifier.ofVanilla("hud/armor_empty");
    private static final Identifier ARMOR_HALF = Identifier.ofVanilla("hud/armor_half");
    private static final Identifier ARMOR_FULL = Identifier.ofVanilla("hud/armor_full");

    /**
     * Hülle für genau ein Vanilla-Element. Fabric wendet die Ersetzungsfunktion jedes Frame auf das frisch erzeugte
     * Vanilla-Element an; statt einer neuen Hülle merkt sich {@link #apply} nur das Ziel und gibt dieselbe Instanz
     * zurück. Dadurch entsteht pro Frame kein Objekt. Nur Render-Thread (apply und render laufen direkt nacheinander);
     * am Frame-Ende lässt {@link HotbarHud#endFrame} das Ziel wieder los.
     */
    private static final class OffsetElement implements HudElement, Function<HudElement, HudElement> {
        private final boolean statusBar;
        private HudElement delegate;

        OffsetElement(boolean statusBar) {
            this.statusBar = statusBar;
        }

        @Override
        public HudElement apply(HudElement original) {
            this.delegate = original;
            return this;
        }

        @Override
        public void render(DrawContext context, RenderTickCounter tickCounter) {
            HudElement target = this.delegate;
            if (target == null) return;
            if (!isActive(statusBar)) {
                target.render(context, tickCounter);
                return;
            }
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            try {
                applyTransform(context);
                target.render(context, tickCounter);
            } finally {
                matrices.popMatrix();
            }
        }
    }

    /**
     * Wegpunkt-Symbole der Locator-Leiste ({@code Bar.renderAddons}): Fabrics Registry umhüllt nur die Leiste selbst.
     * Vanilla holt die aktuelle Leiste dafür ein zweites Mal aus {@code currentBar}; {@code InGameHudMixin} reicht diesen
     * Wert hierher. Ist die Hotbar verschoben, kommt statt der Leiste diese eine vorbereitete Hülle zurück, die nur
     * {@code renderAddons} mit derselben Umrechnung umschließt. Keine Allokation pro Frame, nur Render-Thread.
     */
    private static final class AddonsOffsetBar implements Bar {
        private Bar delegate = Bar.EMPTY;

        @Override
        public int getCenterX(Window window) {
            return delegate.getCenterX(window);
        }

        @Override
        public int getCenterY(Window window) {
            return delegate.getCenterY(window);
        }

        @Override
        public void renderBar(DrawContext context, RenderTickCounter tickCounter) {
            delegate.renderBar(context, tickCounter);
        }

        @Override
        public void renderAddons(DrawContext context, RenderTickCounter tickCounter) {
            Bar target = delegate;
            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            try {
                applyTransform(context);
                target.renderAddons(context, tickCounter);
            } finally {
                matrices.popMatrix();
            }
        }
    }

    private static final AddonsOffsetBar ADDONS_BAR = new AddonsOffsetBar();
    /** Alle registrierten Hüllen (für {@link #endFrame}); wird nur beim Start befüllt. */
    private static final java.util.List<OffsetElement> ELEMENTS = new java.util.ArrayList<>(9);

    /** Aus {@code InGameHudMixin}: Leiste für {@code renderAddons}, bei verschobener Hotbar in der Hülle. */
    public static Object wrapBarForAddons(Object bar) {
        if (!(bar instanceof Bar target) || bar == ADDONS_BAR || !isActive(false)) return bar;
        ADDONS_BAR.delegate = target;
        return ADDONS_BAR;
    }

    /**
     * Ende jedes HUD-Frames ({@code InGameHudMixin}, Ende von {@code InGameHud.render}): gemerkte Ziele loslassen. Fabrics
     * Vanilla-Elemente sind Lambdas, die z. B. den Spieler festhalten; sonst bliebe nach dem Verlassen einer Welt die alte
     * Welt im Speicher, solange das Hauptmenü offen ist. Ohne Allokation (Index-Schleife).
     */
    public static void endFrame() {
        for (int i = 0, n = ELEMENTS.size(); i < n; i++) {
            ELEMENTS.get(i).delegate = null;
        }
        ADDONS_BAR.delegate = Bar.EMPTY;
    }

    /** Einmal beim Client-Start: Vanilla-Elemente der Hotbar-Gruppe umhüllen. */
    public static void register() {
        wrap(VanillaHudElements.HOTBAR, false);
        wrap(VanillaHudElements.INFO_BAR, false);
        wrap(VanillaHudElements.EXPERIENCE_LEVEL, false);
        wrap(VanillaHudElements.ARMOR_BAR, true);
        wrap(VanillaHudElements.HEALTH_BAR, true);
        wrap(VanillaHudElements.FOOD_BAR, true);
        wrap(VanillaHudElements.AIR_BAR, true);
        wrap(VanillaHudElements.MOUNT_HEALTH, true);
        wrap(VanillaHudElements.HELD_ITEM_TOOLTIP, true);
    }

    private static void wrap(Identifier id, boolean statusBar) {
        OffsetElement element = new OffsetElement(statusBar);
        try {
            HudElementRegistry.replaceElement(id, element);
            ELEMENTS.add(element);
        } catch (RuntimeException e) {
            BameClient.LOGGER.warn("Caeser Client: HUD element {} could not be wrapped for the movable hotbar", id, e);
        }
    }

    /**
     * Wird ein Element dieser Gruppe gerade verschoben? Bei Vanilla-Position und -Größe nie (dann keine Matrix-Arbeit),
     * Statusleisten nur mit „Move Status Bars“, im Zuschauermodus nie.
     */
    public static boolean isActive(boolean statusBar) {
        if (statusBar && !HotbarModule.moveStatusBars) return false;
        if (HotbarLayout.isDefault(HotbarModule.offsetX, HotbarModule.offsetY, HotbarModule.scale)) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        return client.interactionManager == null || client.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR;
    }

    /** Matrix-Arbeit mit eigenem Profiler-Abschnitt. Aufrufer: pushMatrix davor, popMatrix im finally. */
    public static void applyTransform(DrawContext context) {
        Profiler profiler = Profilers.get();
        profiler.push(PROFILER_SECTION);
        try {
            applyGroupTransform(context.getMatrices(), context.getScaledWindowWidth(), context.getScaledWindowHeight());
        } finally {
            profiler.pop();
        }
    }

    /** Verschiebung (und Skalierung um die Vanilla-Verankerung unten mittig) auf die Matrix anwenden. */
    public static void applyGroupTransform(Matrix3x2fStack matrices, int screenW, int screenH) {
        float s = HotbarLayout.clampScale(HotbarModule.scale);
        int dx = effectiveOffsetX(screenW);
        int dy = effectiveOffsetY(screenH);
        if (s == 1.0f) {
            matrices.translate(dx, dy);
        } else {
            float ax = HotbarLayout.anchorX(screenW);
            float ay = HotbarLayout.anchorY(screenH);
            matrices.translate(ax + dx, ay + dy);
            matrices.scale(s, s);
            matrices.translate(-ax, -ay);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Rechteck der Gruppe (Spieler-Einstellungen: Haupthand und Angriffsanzeige bestimmen die Breite)
    // ---------------------------------------------------------------------------------------------------------------

    /** Offhand-Feld links? (Vanilla: Seite gegenüber der Haupthand, Rechtshänder links) */
    public static boolean offhandLeft() {
        MinecraftClient client = MinecraftClient.getInstance();
        Arm mainArm = client.player != null ? client.player.getMainArm() : client.options.getMainArm().getValue();
        return mainArm != Arm.LEFT;
    }

    public static boolean attackIndicatorInHotbar() {
        return MinecraftClient.getInstance().options.getAttackIndicator().getValue() == AttackIndicator.HOTBAR;
    }

    public static int groupLeft() {
        return HotbarLayout.left(offhandLeft(), attackIndicatorInHotbar());
    }

    public static int groupRight() {
        return HotbarLayout.right(offhandLeft(), attackIndicatorInHotbar());
    }

    public static int groupTop() {
        return HotbarLayout.top(HotbarModule.moveStatusBars);
    }

    /** Versatz, mit dem gerade gezeichnet wird (auf den Bildschirm begrenzt; gespeicherter Wert bleibt). */
    public static int effectiveOffsetX(int screenW) {
        return HotbarLayout.clampOffsetX(HotbarModule.offsetX, screenW, HotbarModule.scale, groupLeft(), groupRight());
    }

    public static int effectiveOffsetY(int screenH) {
        return HotbarLayout.clampOffsetY(HotbarModule.offsetY, screenH, HotbarModule.scale, groupTop(), HotbarLayout.BOTTOM);
    }

    /** Rechteck der Gruppe, wie sie gerade gezeichnet wird (HUD-Editor: Rahmen, Greifen, Begrenzen). */
    public static HotbarLayout.Box currentBox(int screenW, int screenH) {
        return HotbarLayout.box(effectiveOffsetX(screenW), effectiveOffsetY(screenH), HotbarModule.scale, screenW, screenH,
                groupLeft(), groupRight(), groupTop(), HotbarLayout.BOTTOM);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Vorschau im HUD-Editor (gleiche Vanilla-Koordinaten und gleiche Matrix wie im Spiel, nur nicht weichgezeichnet)
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Zeichnet die Hotbar-Gruppe an der aktuellen Position: Hotbar mit den echten Items (leere Felder bleiben als
     * Vanilla-Feld sichtbar), Offhand-Feld (leer halb durchsichtig, der Platz ist reserviert), Erfahrungsleiste, Level und
     * mit „Move Status Bars“ Herzen, Hunger und Rüstung.
     */
    public static void renderPreview(DrawContext context, int screenW, int screenH) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            applyGroupTransform(matrices, screenW, screenH);
            int cx = screenW / 2;
            int h = screenH;

            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HOTBAR, cx - 91, h - 22, 182, 22);
            int selected = player != null ? player.getInventory().getSelectedSlot() : 0;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HOTBAR_SELECTION, cx - 91 - 1 + selected * 20, h - 23, 24, 23);

            boolean offLeft = offhandLeft();
            ItemStack offhand = player != null ? player.getOffHandStack() : ItemStack.EMPTY;
            int offhandColor = offhand.isEmpty() ? 0x80FFFFFF : 0xFFFFFFFF;
            if (offLeft) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HOTBAR_OFFHAND_LEFT, cx - 91 - 29, h - 23, 29, 24, offhandColor);
            } else {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HOTBAR_OFFHAND_RIGHT, cx + 91, h - 23, 29, 24, offhandColor);
            }

            if (player != null) {
                int itemY = h - 16 - 3;
                for (int slot = 0; slot < 9; slot++) {
                    ItemStack stack = player.getInventory().getStack(slot);
                    if (stack.isEmpty()) continue;
                    int itemX = cx - 90 + slot * 20 + 2;
                    context.drawItem(stack, itemX, itemY);
                    context.drawStackOverlay(client.textRenderer, stack, itemX, itemY);
                }
                if (!offhand.isEmpty()) {
                    int itemX = offLeft ? cx - 91 - 26 : cx + 91 + 10;
                    context.drawItem(offhand, itemX, itemY);
                    context.drawStackOverlay(client.textRenderer, offhand, itemX, itemY);
                }
            }

            int barX = (screenW - 182) / 2;
            int barY = h - 24 - 5;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_BACKGROUND, barX, barY, 182, 5);
            if (player != null) {
                int progress = Math.min(182, (int) (player.experienceProgress * 183.0F));
                if (progress > 0) {
                    context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, 182, 5, 0, 0, barX, barY, progress, 5);
                }
                if (player.experienceLevel > 0) {
                    Bar.drawExperienceLevel(context, client.textRenderer, player.experienceLevel);
                }
            }

            if (HotbarModule.moveStatusBars) {
                renderPreviewStatusBars(context, player, cx, h);
            }
        } finally {
            matrices.popMatrix();
        }
    }

    private static void renderPreviewStatusBars(DrawContext context, PlayerEntity player, int cx, int h) {
        int left = cx - 91;
        int right = cx + 91;
        int rowY = h - 39;

        int health = player != null ? MathHelper.ceil(player.getHealth()) : 20;
        for (int i = 0; i < 10; i++) {
            int x = left + i * 8;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, x, rowY, 9, 9);
            int half = i * 2 + 1;
            if (half < health) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_FULL, x, rowY, 9, 9);
            } else if (half == health) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_HALF, x, rowY, 9, 9);
            }
        }

        int food = player != null ? player.getHungerManager().getFoodLevel() : 20;
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, FOOD_EMPTY, x, rowY, 9, 9);
            int half = i * 2 + 1;
            if (half < food) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, FOOD_FULL, x, rowY, 9, 9);
            } else if (half == food) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, FOOD_HALF, x, rowY, 9, 9);
            }
        }

        int armor = player != null ? player.getArmor() : 0;
        if (armor > 0) {
            int armorY = rowY - 10;
            for (int i = 0; i < 10; i++) {
                int x = left + i * 8;
                int half = i * 2 + 1;
                Identifier sprite = half < armor ? ARMOR_FULL : (half == armor ? ARMOR_HALF : ARMOR_EMPTY);
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, armorY, 9, 9);
            }
        }
    }
}
