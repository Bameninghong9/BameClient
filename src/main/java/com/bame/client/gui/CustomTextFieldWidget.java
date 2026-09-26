package com.bame.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class CustomTextFieldWidget extends ClickableWidget {
    private String text = "";
    private int cursor = 0;
    private int selectionEnd = 0;
    private int offset = 0;
    private Consumer<String> changedListener;
    private String placeholder = "";
    private boolean drawsBackground = true;

    public void setDrawsBackground(boolean value) { this.drawsBackground = value; }

    public CustomTextFieldWidget(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public void setText(String text) {
        this.text = text;
        this.cursor = text.length();
        this.selectionEnd = cursor;
        if (this.changedListener != null) this.changedListener.accept(this.text);
    }

    public String getText() {
        return this.text;
    }

    public void setChangedListener(Consumer<String> changedListener) {
        this.changedListener = changedListener;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.drawsBackground) {
        CustomGuiUtils.fillUltraRounded(context, this.getX(), this.getY(), this.width, this.height, 0xFF0B0E14, 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(context, this.getX(), this.getY(), this.width, this.height, this.isFocused() ? GuiTheme.accent() : 0xFF1E1E24, 4.0f);
        }

        int innerX = this.getX() + 6;
        int innerY = this.getY() + (this.height - 8) / 2;
        int innerWidth = this.width - 12;

        context.enableScissor(innerX, this.getY(), innerX + innerWidth, this.getY() + this.height);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        
        String renderText = this.text;
        if (renderText.isEmpty() && !this.isFocused()) {
            context.drawText(tr, CustomGuiUtils.getFontText(this.placeholder), innerX, innerY, 0xFF555555, false);
            context.disableScissor();
            return;
        }
        
        // Adjust offset if cursor moves out of bounds
        int cursorXOffset = tr.getWidth(CustomGuiUtils.getFontText(renderText.substring(0, this.cursor)));
        if (cursorXOffset - this.offset > innerWidth) {
            this.offset = cursorXOffset - innerWidth;
        } else if (cursorXOffset < this.offset) {
            this.offset = cursorXOffset;
        }

        int drawX = innerX - this.offset;
        
        context.drawText(tr, CustomGuiUtils.getFontText(renderText), drawX, innerY, 0xFFFFFFFF, false);

        // Draw cursor
        if (this.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = drawX + cursorXOffset;
            context.fill(cursorX, innerY - 1, cursorX + 1, innerY + 9, 0xFFFFFFFF);
        }

        // Draw selection (simple)
        if (this.cursor != this.selectionEnd) {
            int min = Math.min(this.cursor, this.selectionEnd);
            int max = Math.max(this.cursor, this.selectionEnd);
            int selStartX = drawX + tr.getWidth(CustomGuiUtils.getFontText(renderText.substring(0, min)));
            int selEndX = drawX + tr.getWidth(CustomGuiUtils.getFontText(renderText.substring(0, max)));
            context.fill(selStartX, innerY - 1, selEndX, innerY + 9, GuiTheme.alpha(GuiTheme.accent(),136));
        }

        context.disableScissor();
    }

    @Override
    public void onClick(Click click, boolean focused) {
        if (!this.active || !this.visible) return;
        this.setFocused(true);
        
        if (click.button() == 0) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            int innerX = this.getX() + 6;
            int clickOffset = (int)click.x() - innerX + this.offset;
            
            this.cursor = 0;
            for (int i = 0; i <= this.text.length(); i++) {
                int w = tr.getWidth(CustomGuiUtils.getFontText(this.text.substring(0, i)));
                if (w > clickOffset) {
                    this.cursor = Math.max(0, i - 1);
                    break;
                }
                this.cursor = i;
            }
            if (!click.hasShift()) {
                this.selectionEnd = this.cursor;
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (!this.active || !this.visible) return false;
        boolean clicked = click.x() >= this.getX() && click.x() < this.getX() + this.width && click.y() >= this.getY() && click.y() < this.getY() + this.height;
        if (clicked) {
            this.setFocused(true);
            this.onClick(click, bl);
            return true;
        } else {
            this.setFocused(false);
            return false;
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!this.isFocused()) return false;
        
        int keyCode = input.key();
        int modifiers = input.modifiers();
        boolean ctrl = (modifiers & 2) != 0;
        boolean shift = (modifiers & 1) != 0;

        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            this.cursor = this.text.length();
            this.selectionEnd = 0;
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            MinecraftClient.getInstance().keyboard.setClipboard(this.getSelectedText());
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            this.write(MinecraftClient.getInstance().keyboard.getClipboard());
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            MinecraftClient.getInstance().keyboard.setClipboard(this.getSelectedText());
            this.write("");
            return true;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (this.cursor != this.selectionEnd) {
                    this.write("");
                } else if (this.cursor > 0) {
                    this.text = this.text.substring(0, this.cursor - 1) + this.text.substring(this.cursor);
                    this.cursor--;
                    this.selectionEnd = this.cursor;
                    if (this.changedListener != null) this.changedListener.accept(this.text);
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (this.cursor != this.selectionEnd) {
                    this.write("");
                } else if (this.cursor < this.text.length()) {
                    this.text = this.text.substring(0, this.cursor) + this.text.substring(this.cursor + 1);
                    if (this.changedListener != null) this.changedListener.accept(this.text);
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                if (this.cursor > 0) this.cursor--;
                if (!shift) this.selectionEnd = this.cursor;
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                if (this.cursor < this.text.length()) this.cursor++;
                if (!shift) this.selectionEnd = this.cursor;
                return true;
            case GLFW.GLFW_KEY_HOME:
                this.cursor = 0;
                if (!shift) this.selectionEnd = this.cursor;
                return true;
            case GLFW.GLFW_KEY_END:
                this.cursor = this.text.length();
                if (!shift) this.selectionEnd = this.cursor;
                return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!this.isFocused()) return false;
        if (input.isValidChar()) {
            this.write(input.asString());
            return true;
        }
        return false;
    }

    private String getSelectedText() {
        int min = Math.min(this.cursor, this.selectionEnd);
        int max = Math.max(this.cursor, this.selectionEnd);
        return this.text.substring(min, max);
    }

    private void write(String string) {
        int min = Math.min(this.cursor, this.selectionEnd);
        int max = Math.max(this.cursor, this.selectionEnd);
        this.text = this.text.substring(0, min) + string + this.text.substring(max);
        this.cursor = min + string.length();
        this.selectionEnd = this.cursor;
        if (this.changedListener != null) this.changedListener.accept(this.text);
    }

    @Override
    protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {}
}
