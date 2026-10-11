package com.bame.client.gui.search;

import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.gui.CustomTextFieldWidget;
import com.bame.client.gui.GuiTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Caeser-style search field (rounded text field with a small magnifier icon on the left). */
public class SearchFieldWidget extends CustomTextFieldWidget {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 16;
    private static final int ICON_SIZE = 14;

    public SearchFieldWidget(String placeholder) {
        super(0, 0, WIDTH, HEIGHT, Text.literal(placeholder));
        this.setPlaceholder(placeholder);
        this.setTextInsetLeft(ICON_SIZE);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        int iconColor = this.isFocused() ? GuiTheme.accent() : 0xFF8E95A4;
        CustomGuiUtils.drawSearchIcon(context, this.getX() + 4, this.getY() + (this.height - ICON_SIZE) / 2, iconColor);
    }
}
