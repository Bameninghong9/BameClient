package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import net.minecraft.client.gui.DrawContext;

final class AmbientLighting {
    private long lastFrame=System.nanoTime();
    private double phase;
    void render(DrawContext c,int x,int y,int w,int h) {
        long now=System.nanoTime(); double dt=Math.min(.1,(now-lastFrame)/1e9); lastFrame=now;
        if(BameClientConfig.ambientAnimation) phase+=dt*BameClientConfig.animationSpeed*.45;
        if(!BameClientConfig.ambientBackground) return;
        int primary=BameClientConfig.ambientMode==0?GuiTheme.accent():BameClientConfig.ambientPrimary;
        int secondary=BameClientConfig.ambientMode==0?(0xFF000000|GuiTheme.current().secondary()):
            BameClientConfig.ambientMode==2?BameClientConfig.ambientSecondary:primary;
        float radius=.15f+.85f*BameClientConfig.ambientRadius;
        c.enableScissor(x,y,x+w,y+h);
        glow(c,x+w*(.28+.08*Math.sin(phase)),y+h*(.24+.10*Math.cos(phase*.7)),w*radius*.72,h*radius,primary);
        glow(c,x+w*(.77+.07*Math.cos(phase*.8)),y+h*(.64+.10*Math.sin(phase*.6)),w*radius*.62,h*radius,secondary);
        c.disableScissor();
    }
    private void glow(DrawContext c,double x,double y,double rx,double ry,int color) {
        float light=BameClientConfig.ambientIntensity;
        int rgb=(Math.round((color>>16&255)*light)<<16)|(Math.round((color>>8&255)*light)<<8)|Math.round((color&255)*light);
        int alpha=Math.round((color>>>24)*BameClientConfig.ambientOpacity*.32f);
        if(alpha==0||light==0) return;
        for(int i=0;i<48;i++) {
            double t=(i+.5)/24-1,falloff=Math.pow(Math.max(0,1-t*t),2);
            int a=(int)(alpha*falloff),left=(int)(x-rx+2*rx*i/48),right=(int)(x-rx+2*rx*(i+1)/48);
            c.fillGradient(left,(int)(y-ry),right,(int)y,rgb,(a<<24)|rgb);
            c.fillGradient(left,(int)y,right,(int)(y+ry),(a<<24)|rgb,rgb);
        }
    }
}
