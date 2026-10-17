package com.bame.client.gui.search;

import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.gui.GuiTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Suchfeld der Container-Suche im Caeser-Stil wie {@link SearchFieldWidget} (Lupe links, abgerundet, Akzentfarbe beim
 * Fokus), hier dunkel und leicht transparent mit dezentem Leuchten beim Fokus. Rechts innen stehen bei Text die
 * Trefferzahl und ein kleines „×“ zum Leeren.
 *
 * <p>Das Feld zeichnet und bearbeitet nur Text; Maus und Tastatur leitet {@link ContainerSearch} über die
 * Fabric-Screen-Events weiter (damit Klicks und Tasten nie zusätzlich als Inventar-Aktion ankommen).
 */
public class ContainerSearchField extends SearchFieldWidget {
    private static final Identifier CLEAR_ICON = Identifier.of("bameclient", "textures/gui/icons/toast_cross.png");
    private static final int CLEAR_ICON_SIZE = 7;
    /** Klickfläche des „×“ am rechten Rand (volle Feldhöhe). */
    private static final int CLEAR_HIT_WIDTH = 13;
    /** Platz der Lupe links ({@link SearchFieldWidget}) plus Innenrand des Textfelds (6 px je Seite). */
    private static final int TEXT_PADDING = 14 + 12;
    /** Die Trefferzahl entfällt, wenn sonst weniger Platz für den Text bliebe (schmale Leiste neben dem Fenster). */
    private static final int MIN_TEXT_SPACE = 32;
    private static final int BACKGROUND = 0xD80B0E14;
    private static final int OUTLINE = 0xFF2C3140;
    private static final int OUTLINE_HOVER = 0xFF4A5163;
    private static final int COUNT_COLOR = 0xFF8E95A4;
    private static final int COUNT_NONE_COLOR = 0xFFE57373;
    private static final int CLEAR_COLOR = 0xFF8E95A4;
    private static final int CLEAR_HOVER_COLOR = 0xFFFFFFFF;

    private int matchCount = -1;
    private Text countText = null;
    /** Breite von {@link #countText}, einmal gemessen pro neuer Zahl ({@code -1} = noch nicht gemessen). */
    private int countWidth = -1;
    private boolean rendered = false;

    public ContainerSearchField(String placeholder) {
        super(placeholder);
        this.setDrawsBackground(false);
    }

    /** Trefferzahl rechts im Feld ({@code -1} = keine Anzeige). Der Text wird nur bei geänderter Zahl neu erstellt. */
    public void setMatchCount(int count) {
        if (count == this.matchCount) return;
        this.matchCount = count;
        this.countText = count >= 0 ? CustomGuiUtils.getFontText(Integer.toString(count)) : null;
        this.countWidth = -1;
    }

    public int getMatchCount() {
        return this.matchCount;
    }

    /** Liegt der Punkt auf dem „×“ (nur sichtbar, wenn Text im Feld steht)? */
    public boolean isOverClearButton(double mouseX, double mouseY) {
        return this.visible && !this.getText().isEmpty()
                && mouseX >= this.getX() + this.width - CLEAR_HIT_WIDTH && mouseX < this.getX() + this.width
                && mouseY >= this.getY() && mouseY < this.getY() + this.height;
    }

    /** Wurde das Feld seit dem letzten Aufruf gezeichnet? (Setzt die Markierung zurück.) */
    public boolean consumeRendered() {
        boolean r = this.rendered;
        this.rendered = false;
        return r;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        this.rendered = true;
        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;
        boolean focused = this.isFocused();
        int accent = GuiTheme.accent();

        if (focused) {
            CustomGuiUtils.drawUltraRoundedOutline(context, x - 1, y - 1, w + 2, h + 2, GuiTheme.alpha(accent, 0x50), 5.0f);
        }
        CustomGuiUtils.fillUltraRounded(context, x, y, w, h, BACKGROUND, 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(context, x, y, w, h,
                focused ? accent : (this.isHovered() ? OUTLINE_HOVER : OUTLINE), 4.0f);

        boolean hasText = !this.getText().isEmpty();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Text count = hasText ? this.countText : null;
        if (count != null && this.countWidth < 0) this.countWidth = tr.getWidth(count);
        int countW = count != null ? this.countWidth : 0;
        int clearInset = hasText ? CLEAR_HIT_WIDTH - 4 : 0; // CustomTextFieldWidget hat rechts schon 6 px Rand
        if (count != null && w - TEXT_PADDING - clearInset - (countW + 4) < MIN_TEXT_SPACE) {
            count = null;
            countW = 0;
        }
        // Text nicht unter Trefferzahl und "×" laufen lassen
        this.setTextInsetRight(clearInset + (count != null ? countW + 4 : 0));

        super.renderWidget(context, mouseX, mouseY, delta);

        if (hasText) {
            boolean clearHover = this.isHovered() && this.isOverClearButton(mouseX, mouseY);
            int iconX = x + w - CLEAR_HIT_WIDTH + (CLEAR_HIT_WIDTH - CLEAR_ICON_SIZE) / 2;
            int iconY = y + (h - CLEAR_ICON_SIZE) / 2;
            context.drawTexture(RenderPipelines.GUI_TEXTURED, CLEAR_ICON, iconX, iconY, 0.0f, 0.0f,
                    CLEAR_ICON_SIZE, CLEAR_ICON_SIZE, 128, 128, 128, 128, clearHover ? CLEAR_HOVER_COLOR : CLEAR_COLOR);
            if (count != null) {
                context.drawText(tr, count, x + w - CLEAR_HIT_WIDTH - countW - 1, y + (h - 8) / 2,
                        this.matchCount == 0 ? COUNT_NONE_COLOR : COUNT_COLOR, false);
            }
        }

        if (this.isHovered()) {
            context.setCursor(this.isOverClearButton(mouseX, mouseY) ? StandardCursors.POINTING_HAND : StandardCursors.IBEAM);
        }
    }

    /** Nicht per Tab/Pfeiltasten erreichbar: Fokus nur per Klick oder Strg+F. */
    @Override
    public GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
        return null;
    }
}
