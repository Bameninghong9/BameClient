package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import net.minecraft.client.gui.DrawContext;
import java.awt.Color;

/** HSV picker with independent alpha; writes config once at the end of a drag. */
final class OutlineColorPicker {
    private float hue,saturation,value,alpha;
    private int drag=-1;
    int x,y,width;
    private final java.util.function.IntSupplier getter;
    private final java.util.function.IntConsumer setter;
    OutlineColorPicker() { this(()->BameClientConfig.outlineColor,c->BameClientConfig.outlineColor=c); }
    OutlineColorPicker(java.util.function.IntSupplier getter,java.util.function.IntConsumer setter) {
        this.getter=getter; this.setter=setter;
        int c=getter.getAsInt();
        float[] hsv=Color.RGBtoHSB(c>>16&255,c>>8&255,c&255,null);
        hue=hsv[0]; saturation=hsv[1]; value=hsv[2]; alpha=(c>>>24)/255f;
    }
    void layout(int x,int y,int width) { this.x=x; this.y=y; this.width=width; }
    void render(DrawContext c) {
        // Vertical gradients per column avoid a separate draw call for every SV pixel.
        for(int i=0;i<width;i++) {
            int top=Color.HSBtoRGB(hue,i/(float)(width-1),1);
            c.fillGradient(x+i,y,x+i+1,y+64,top,0xFF000000);
            c.fill(x+i,y+72,x+i+1,y+80,Color.HSBtoRGB(i/(float)(width-1),1,1));
        }
        for(int i=0;i<width;i+=4) for(int j=0;j<8;j+=4)
            c.fill(x+i,y+88+j,x+Math.min(width,i+4),y+92+j,((i/4+j/4)%2==0)?0xFFAAAAAA:0xFF555555);
        for(int i=0;i<width;i++) c.fill(x+i,y+88,x+i+1,y+96,
            GuiTheme.alpha(getter.getAsInt(),Math.round(255*i/(float)(width-1))));
        CustomGuiUtils.drawSmoothRing(c,x+Math.round(saturation*(width-1)),y+Math.round((1-value)*63),3,1.2f,0xFFFFFFFF);
        CustomGuiUtils.drawSmoothRing(c,x+Math.round(hue*(width-1)),y+76,4,1.2f,0xFFFFFFFF);
        CustomGuiUtils.drawSmoothRing(c,x+Math.round(alpha*(width-1)),y+92,4,1.2f,0xFFFFFFFF);
    }
    boolean click(double mx,double my) {
        if(mx<x-4||mx>x+width+4) return false;
        if(my>=y&&my<y+64) drag=0;
        else if(my>=y+68&&my<y+84) drag=1;
        else if(my>=y+84&&my<y+100) drag=2;
        else return false;
        update(mx,my); return true;
    }
    boolean dragging() { return drag>=0; }
    void update(double mx,double my) {
        float t=(float)Math.clamp((mx-x)/(width-1),0,1);
        if(drag==0) { saturation=t; value=1-(float)Math.clamp((my-y)/63,0,1); }
        if(drag==1) hue=t;
        if(drag==2) alpha=t;
        setter.accept((Math.round(alpha*255)<<24)|(Color.HSBtoRGB(hue,saturation,value)&0xFFFFFF));
    }
    void release() { if(drag>=0) { drag=-1; BameClientConfig.save(); } }
}
