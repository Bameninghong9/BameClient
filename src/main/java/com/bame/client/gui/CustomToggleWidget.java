package com.bame.client.gui;

import net.minecraft.client.gui.DrawContext;

public class CustomToggleWidget {
    public int x, y, width, height;
    public boolean enabled;

    public CustomToggleWidget(int x, int y, int width, int height, boolean enabled) {
        this.x = x; 
        this.y = y; 
        this.width = width; 
        this.height = height; 
        this.enabled = enabled;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int bgColor = enabled ? GuiTheme.accent() : 0xFF2A2D35; // Green when on, grey when off
        int knobColor = enabled ? 0xFFFFFFFF : 0xFF8A90A5; // White when on, lighter grey when off
        
        if (com.bame.client.BameClientConfig.classicToggle) {
            CustomGuiUtils.fillUltraRounded(context,x,y,width,height,bgColor,3);
            String mark=enabled ? "ON" : "OFF";
            var tr=net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            var label=CustomGuiUtils.getFontText(mark);
            context.drawText(tr,label,x+(width-tr.getWidth(label))/2,y+(height-8)/2,0xFFFFFFFF,false);
            return;
        }
        // Switch Background
        CustomGuiUtils.fillUltraRounded(context, x, y, width, height, bgColor);
        
        // Knob
        int knobSize = height - 4;
        int knobX = enabled ? x + width - knobSize - 2 : x + 2;
        CustomGuiUtils.fillUltraRounded(context, knobX, y + 2, knobSize, knobSize, knobColor);
    }
    
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            this.enabled = !this.enabled;
            return true;
        }
        return false;
    }
}
