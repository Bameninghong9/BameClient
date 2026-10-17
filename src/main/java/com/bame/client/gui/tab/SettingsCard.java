package com.bame.client.gui.tab;

import com.bame.client.BameClientConfig;
import com.bame.client.gui.BameClientScreen;
import com.bame.client.gui.CustomGuiUtils;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Modul-Karte im Stil der übrigen Visuals-Karten (Kopf mit Schalter, aufklappbar) mit einer Liste von Einstellungen:
 * Taste, dann Farbe/Schalter/Schieberegler je 26 px, unten „Reset“. Zeichnen und Klicks nutzen dieselbe Geometrie.
 */
final class SettingsCard {
    sealed interface Row permits Toggle, Slider, ColorRow, Choice {}

    /** Auswahl aus mehreren Werten: Klick auf den Knopf rechts schaltet zum nächsten Wert. */
    record Choice(String label, java.util.function.Supplier<String> value, Runnable next) implements Row {}

    record Toggle(String label, BooleanSupplier get, Consumer<Boolean> set) implements Row {}

    /** Schieberegler von {@code min} bis {@code max} in Schritten von {@code step}; Anzeige über {@code format}. */
    record Slider(String label, float min, float max, float step, java.util.function.Function<Float, String> format,
                  java.util.function.Supplier<Float> get, Consumer<Float> set) implements Row {}

    record ColorRow(String label, String pickerTitle, IntSupplier get, IntConsumer set) implements Row {}

    private static final int ROW = 26;
    private static final int SLIDER_LABEL_W = 104;
    private int labelColumnFor = -1;
    private int labelColumn = SLIDER_LABEL_W;

    /**
     * Abstand vom Kartenrand bis zum Schieberegler: so breit wie die längste Beschriftung der Karte (mit Wert), damit
     * sich Text und Regler nicht überlappen; der Regler bleibt mindestens 50 px breit. Gleich für Zeichnen und Klicks.
     */
    private int labelColumn(int w) {
        if (labelColumnFor == w) return labelColumn;
        net.minecraft.client.font.TextRenderer tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        int widest = 0;
        for (Row row : rows) {
            if (row instanceof Slider s) {
                String sample = s.label() + ": " + s.format().apply(s.max());
                widest = Math.max(widest, tr.getWidth(CustomGuiUtils.getFontText(sample)));
            }
        }
        labelColumn = Math.clamp(14 + widest + 10, SLIDER_LABEL_W, Math.max(SLIDER_LABEL_W, w - 64));
        labelColumnFor = w;
        return labelColumn;
    }
    private static final int CHOICE_W = 74;

    private final String keyId;
    private final String title;
    private final String desc;
    private final List<String> searchWords;
    private final BooleanSupplier enabled;
    private final Runnable toggleEnabled;
    private final BooleanSupplier expanded;
    private final Runnable toggleExpanded;
    private final IntSupplier keyBind;
    private final IntConsumer setKeyBind;
    private final Runnable reset;
    private final List<Row> rows;

    private boolean listening = false;
    private Slider dragging = null;
    private int dragX, dragW;

    SettingsCard(String keyId, String title, String desc, List<String> searchWords,
                 BooleanSupplier enabled, Runnable toggleEnabled, BooleanSupplier expanded, Runnable toggleExpanded,
                 IntSupplier keyBind, IntConsumer setKeyBind, Runnable reset, List<Row> rows) {
        this.keyId = keyId;
        this.title = title;
        this.desc = desc;
        this.searchWords = searchWords;
        this.enabled = enabled;
        this.toggleEnabled = toggleEnabled;
        this.expanded = expanded;
        this.toggleExpanded = toggleExpanded;
        this.keyBind = keyBind;
        this.setKeyBind = setKeyBind;
        this.reset = reset;
        this.rows = rows;
    }

    boolean visible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (q.isEmpty() || "visuals".contains(q)) return true;
        for (String w : searchWords) if (w.contains(q)) return true;
        return false;
    }

    int height() {
        return expanded.getAsBoolean() ? 54 + ROW * (1 + rows.size()) + 24 : 50;
    }

    boolean isListening() { return listening; }

    void stopListening() { listening = false; }

    void startListening() { listening = true; }

    void render(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        screen.moduleCard(c, x, y, w, height(), enabled.getAsBoolean(), expanded.getAsBoolean(), title, desc, null, "visuals", mx, my);
        if (!expanded.getAsBoolean()) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        screen.keybindButton(c, keyId, keyBind.getAsInt(), listening, x + 64, curY, 48, 16, mx, my);
        curY += ROW;

        for (Row row : rows) {
            switch (row) {
                case Toggle t -> {
                    screen.text(c, t.label(), x + 14, curY + 4, 0xFFD4D8E0);
                    screen.toggle(c, x + w - 38, curY + 2, t.get().getAsBoolean(), mx, my, delta);
                }
                case ColorRow cr -> {
                    screen.text(c, cr.label(), x + 14, curY + 4, 0xFFD4D8E0);
                    int sx = x + w - 32;
                    boolean hover = screen.inside(mx, my, sx, curY + 2, 16, 16);
                    c.fill(sx, curY + 2, sx + 16, curY + 18, 0xFF000000 | (cr.get().getAsInt() & 0xFFFFFF));
                    CustomGuiUtils.drawUltraRoundedOutline(c, sx, curY + 2, 16, 16, hover ? 0xFFFFFFFF : 0xFF353C4D, 2);
                }
                case Slider s -> {
                    screen.text(c, s.label() + ": " + s.format().apply(s.get().get()), x + 14, curY + 4, 0xFFD4D8E0);
                    int sx = x + labelColumn(w);
                    int sw = w - labelColumn(w) - 14;
                    screen.drawCustomSlider(c, sx, curY + 6, sw, (s.get().get() - s.min()) / (s.max() - s.min()));
                }
                case Choice ch -> {
                    screen.text(c, ch.label(), x + 14, curY + 4, 0xFFD4D8E0);
                    screen.modeButton(c, ch.value().get(), x + w - 12 - CHOICE_W, curY + 1, CHOICE_W, 16, mx, my, true);
                }
            }
            curY += ROW;
        }
        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    boolean mouseClicked(BameClientScreen screen, double mx, double my, int x, int y, int w) {
        if (screen.handleCardHeaderClick((int) mx, (int) my, x, y, w, toggleEnabled, toggleExpanded)) return true;
        if (!expanded.getAsBoolean()) return false;

        int curY = y + 54;
        if (screen.inside(mx, my, x + 64, curY, 48, 16)) {
            listening = true;
            return true;
        }
        curY += ROW;

        for (Row row : rows) {
            switch (row) {
                case Toggle t -> {
                    if (screen.inside(mx, my, x + w - 38, curY + 2, 26, 14)) {
                        t.set().accept(!t.get().getAsBoolean());
                        BameClientConfig.save();
                        return true;
                    }
                }
                case ColorRow cr -> {
                    if (screen.inside(mx, my, x + w - 32, curY + 2, 16, 16)) {
                        screen.openColorPicker(cr.pickerTitle(), cr.get(), cr.set());
                        return true;
                    }
                }
                case Choice ch -> {
                    if (screen.inside(mx, my, x + w - 12 - CHOICE_W, curY + 1, CHOICE_W, 16)) {
                        ch.next().run();
                        com.bame.client.sound.ClientSoundManager.playClick();
                        BameClientConfig.save();
                        return true;
                    }
                }
                case Slider s -> {
                    int sx = x + labelColumn(w);
                    int sw = w - labelColumn(w) - 14;
                    if (screen.inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                        dragging = s;
                        dragX = sx;
                        dragW = sw;
                        apply(s, mx);
                        return true;
                    }
                }
            }
            curY += ROW;
        }
        if (screen.inside(mx, my, x + w - 58, curY, 46, 16)) {
            reset.run();
            BameClientConfig.save();
            return true;
        }
        return false;
    }

    boolean mouseDragged(double mx) {
        if (dragging == null) return false;
        apply(dragging, mx);
        return true;
    }

    /** Ende des Ziehens: gespeichert wird einmal beim Loslassen. */
    boolean mouseReleased() {
        if (dragging == null) return false;
        dragging = null;
        BameClientConfig.save();
        return true;
    }

    boolean keyPressed(int key) {
        if (!listening) return false;
        setKeyBind.accept(key == GLFW.GLFW_KEY_ESCAPE ? -1 : key);
        listening = false;
        BameClientConfig.save();
        return true;
    }

    private void apply(Slider s, double mx) {
        float f = (float) Math.clamp((mx - dragX) / (double) dragW, 0.0, 1.0);
        float v = s.min() + f * (s.max() - s.min());
        v = Math.round(v / s.step()) * s.step();
        s.set().accept(Math.clamp(v, s.min(), s.max()));
    }
}
