package com.bame.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

public class CustomButtonWidget implements Drawable, Element, Selectable {
    private final int x, y, width, height;
    private Text message;
    private final Runnable onPress;
    private boolean hovered;

    public void setMessage(Text message) {
        this.message = message;
    }

    public CustomButtonWidget(int x, int y, int width, int height, Text message, Runnable onPress) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.message = message;
        this.onPress = onPress;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
        
        int bgColor = this.hovered ? 0xFF2A2A33 : 0xFF15151A;
        int borderColor = this.hovered ? GuiTheme.accent() : 0xFF1E1E24; 
        int textColor = this.hovered ? 0xFFFFFFFF : 0xFFAAAAAA;

        CustomGuiUtils.fillUltraRounded(context, this.x, this.y, this.width, this.height, bgColor, 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(context, this.x, this.y, this.width, this.height, borderColor, 4.0f);

        Text label = CustomGuiUtils.getFontText(this.message.getString());
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(label);
        int textX = this.x + (this.width - textWidth) / 2;
        int textY = this.y + (this.height - 8) / 2;

        context.drawText(MinecraftClient.getInstance().textRenderer, label, textX, textY, textColor, false);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean bl) {
        double mx = click.x();
        double my = click.y();
        if (mx >= this.x && mx <= this.x + this.width && my >= this.y && my <= this.y + this.height) {
            if (click.button() == 0) {
                if (this.onPress != null) this.onPress.run();
                return true;
            }
        }
        return false;
    }

    public void setFocused(boolean focused) {}

    public boolean isFocused() { return false; }

    public SelectionType getType() { return SelectionType.NONE; }

    public void appendNarrations(NarrationMessageBuilder builder) {}
}
