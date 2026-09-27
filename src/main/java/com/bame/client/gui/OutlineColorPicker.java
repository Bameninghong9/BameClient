package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import net.minecraft.client.gui.DrawContext;
import java.awt.Color;

/** Sleek HSV + Alpha color picker with clean handles and crisp rendering. */
final class OutlineColorPicker {
    private float hue, saturation, value, alpha;
    private int drag = -1;
    int x, y, width;
    private final java.util.function.IntSupplier getter;
    private final java.util.function.IntConsumer setter;

    OutlineColorPicker() {
        this(() -> BameClientConfig.outlineColor, c -> BameClientConfig.outlineColor = c);
    }

    OutlineColorPicker(java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        this.getter = getter;
        this.setter = setter;
        syncFromGetter();
    }

    private void syncFromGetter() {
        int c = getter.getAsInt();
        float[] hsv = Color.RGBtoHSB((c >> 16) & 255, (c >> 8) & 255, c & 255, null);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        alpha = ((c >>> 24) & 255) / 255f;
    }

    void layout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    void render(DrawContext c) {
        if (drag < 0) {
            syncFromGetter();
        }

        // 1. SV Box (Top)
        for (int i = 0; i < width; i++) {
            int top = Color.HSBtoRGB(hue, i / (float)(width - 1), 1.0f);
            c.fillGradient(x + i, y, x + i + 1, y + 32, top, 0xFF000000);
        }
        CustomGuiUtils.drawRoundedOutline(c, x - 1, y - 1, width + 2, 34, 0xFF292D36);

        // 2. Hue Bar (Middle)
        for (int i = 0; i < width; i++) {
            int rgb = Color.HSBtoRGB(i / (float)(width - 1), 1.0f, 1.0f);
            c.fill(x + i, y + 38, x + i + 1, y + 46, rgb);
        }
        CustomGuiUtils.drawRoundedOutline(c, x - 1, y + 37, width + 2, 10, 0xFF292D36);

        // 3. Alpha Bar (Bottom with checkerboard background)
        for (int i = 0; i < width; i += 4) {
            for (int j = 0; j < 8; j += 4) {
                int col = ((i / 4 + j / 4) % 2 == 0) ? 0xFFAAAAAA : 0xFF555555;
                c.fill(x + i, y + 52 + j, x + Math.min(width, i + 4), y + 52 + j + 4, col);
            }
        }
        int curRgb = Color.HSBtoRGB(hue, saturation, value);
        for (int i = 0; i < width; i++) {
            int a = Math.round(255 * i / (float)(width - 1));
            c.fill(x + i, y + 52, x + i + 1, y + 60, (a << 24) | (curRgb & 0xFFFFFF));
        }
        CustomGuiUtils.drawRoundedOutline(c, x - 1, y + 51, width + 2, 10, 0xFF292D36);

        // --- HANDLES ---

        // SV Handle (Circle Indicator)
        int svX = x + Math.round(saturation * (width - 1));
        int svY = y + Math.round((1.0f - value) * 31);
        drawSvHandle(c, svX, svY);

        // Hue Handle (Slider Knob)
        int hueX = x + Math.round(hue * (width - 1));
        drawBarHandle(c, hueX, y + 42);

        // Alpha Handle (Slider Knob)
        int alphaX = x + Math.round(alpha * (width - 1));
        drawBarHandle(c, alphaX, y + 56);
    }

    private void drawSvHandle(DrawContext c, int hx, int hy) {
        c.fill(hx - 4, hy - 4, hx + 5, hy + 5, 0xAA000000);
        c.fill(hx - 3, hy - 3, hx + 4, hy + 4, 0xFFFFFFFF);
        c.fill(hx - 2, hy - 2, hx + 3, hy + 3, 0xFF12161E);
    }

    private void drawBarHandle(DrawContext c, int hx, int hy) {
        // Vertical sleek slider pill handle
        int w = 6;
        int h = 12;
        int left = hx - w / 2;
        int top = hy - h / 2;

        c.fill(left - 1, top - 1, left + w + 1, top + h + 1, 0xFF000000);
        c.fill(left, top, left + w, top + h, 0xFFFFFFFF);
    }

    boolean click(double mx, double my) {
        if (mx < x - 6 || mx > x + width + 6) return false;
        if (my >= y && my < y + 34) drag = 0;
        else if (my >= y + 36 && my < y + 48) drag = 1;
        else if (my >= y + 50 && my < y + 64) drag = 2;
        else return false;

        update(mx, my);
        return true;
    }

    boolean dragging() {
        return drag >= 0;
    }

    void update(double mx, double my) {
        float t = (float) Math.clamp((mx - x) / (float)(width - 1), 0.0, 1.0);
        if (drag == 0) {
            saturation = t;
            value = 1.0f - (float) Math.clamp((my - y) / 31.0, 0.0, 1.0);
        }
        if (drag == 1) hue = t;
        if (drag == 2) alpha = t;

        int newColor = (Math.round(alpha * 255) << 24) | (Color.HSBtoRGB(hue, saturation, value) & 0xFFFFFF);
        setter.accept(newColor);
    }

    void release() {
        if (drag >= 0) {
            drag = -1;
            BameClientConfig.save();
        }
    }
}
