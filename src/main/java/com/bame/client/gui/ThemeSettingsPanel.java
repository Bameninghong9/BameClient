package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Shared geometry for drawing, hit testing and expanding inline colour controls. */
final class ThemeSettingsPanel {
    private int x,y,w,open=-1,slider=-1;
    private OutlineColorPicker colorPicker;
    private int half() { return (w-12)/2; }
    private int right() { return x+half()+12; }
    private int appearanceHeight() { return 168+(open==0?116:0); }
    private int ambientTop() { return y+Math.max(appearanceHeight(),168)+12; }
    private int ambientHeight() { return open==1?232:open==2?264:174; }
    private int motionTop() { return ambientTop()+ambientHeight()+12; }
    int height() { return motionTop()-y+90; }
    private int colorY(int i) { return i==0?y+38:ambientTop()+38+(i==2?32+(open==1?116:0):0); }
    private int groupY(int i) { return y+(i==0?74:120)+(open==0?116:0); }
    private int sliderY(int i) { return i==3?motionTop()+43:ambientTop()+34+i*43; }
    private int sliderX() { return right()+12; }
    private int sliderWidth() { return w-(sliderX()-x)-44; }
    void layout(int x,int y,int w) {
        this.x=x; this.y=y; this.w=w;
        if(colorPicker!=null) colorPicker.layout(x+14,colorY(open)+30,half()-28);
    }
    private void text(DrawContext c,String s,int x,int y,int color) {
        c.drawText(MinecraftClient.getInstance().textRenderer,CustomGuiUtils.getFontText(s),x,y,color,false);
    }
    private void card(DrawContext c,int xx,int yy,int width,int height,String title) {
        CustomGuiUtils.fillUltraRounded(c,xx,yy,width,height,0xE812161E,6);
        text(c,title,xx+12,yy+12,0xFFB5BCC9);
    }
    private boolean hit(double mx,double my,int xx,int yy,int ww,int hh) { return mx>=xx&&mx<xx+ww&&my>=yy&&my<yy+hh; }
    private void swatch(DrawContext c,String label,int index,int color) {
        int yy=colorY(index);
        c.enableScissor(x+10,yy,x+half()-39,yy+22);
        text(c,label,x+12,yy+6,0xFFD4D8E0);
        c.disableScissor();
        CustomGuiUtils.fillUltraRounded(c,x+half()-34,yy,22,22,0xFF777777,4);
        CustomGuiUtils.fillUltraRounded(c,x+half()-34,yy,22,22,color,4);
        CustomGuiUtils.drawUltraRoundedOutline(c,x+half()-34,yy,22,22,open==index?0xFFFFFFFF:0xFF9BA2AF,4);
    }
    private void segments(DrawContext c,String label,int yy,String[] labels,int selected) {
        text(c,label,x+12,yy,0xFFD4D8E0);
        int ww=(half()-24)/labels.length;
        for(int i=0;i<labels.length;i++) {
            int xx=x+12+i*ww;
            CustomGuiUtils.fillUltraRounded(c,xx,yy+15,ww-3,22,i==selected?GuiTheme.accent():0xFF0C1017,4);
            int tw=MinecraftClient.getInstance().textRenderer.getWidth(CustomGuiUtils.getFontText(labels[i]));
            text(c,labels[i],xx+(ww-3-tw)/2,yy+22,0xFFFFFFFF);
        }
    }
    private float value(int i) { return switch(i) {
        case 0 -> BameClientConfig.ambientIntensity; case 1 -> BameClientConfig.ambientOpacity;
        case 2 -> BameClientConfig.ambientRadius; default -> BameClientConfig.animationSpeed; }; }
    void render(DrawContext c,int mx,int my,float delta) {
        card(c,x,y,half(),appearanceHeight(),"APPEARANCE");
        swatch(c,"Main Color",0,GuiTheme.accent());
        segments(c,"Toggle Style",groupY(0),new String[]{"Modern","Classic"},BameClientConfig.classicToggle?1:0);
        segments(c,"Ambient Color Mode",groupY(1),new String[]{"Theme","Custom","Two Color"},BameClientConfig.ambientMode);
        card(c,right(),y,w-half()-12,168,"EFFECTS");
        String[] effectLabels={"See-Through GUI","Frosted Blur","Ambient Background"};
        boolean[] enabled={BameClientConfig.seeThrough,BameClientConfig.frostedBlur,BameClientConfig.ambientBackground};
        for(int i=0;i<3;i++) {
            int ey=y+40+i*40;
            c.enableScissor(right()+10,ey,x+w-46,ey+28);
            text(c,effectLabels[i],right()+12,ey+2,0xFFD4D8E0);
            c.disableScissor();
            new CustomToggleWidget(x+w-40,ey,26,14,enabled[i]).render(c,mx,my,delta);
        }
        card(c,x,ambientTop(),w,ambientHeight(),"AMBIENT LIGHTING");
        swatch(c,"Ambient Primary Color",1,BameClientConfig.ambientPrimary);
        swatch(c,"Ambient Secondary Color",2,BameClientConfig.ambientSecondary);
        card(c,x,motionTop(),w,90,"MOTION");
        text(c,"Ambient Animation",x+12,motionTop()+38,0xFFD4D8E0);
        new CustomToggleWidget(x+half()-38,motionTop()+54,26,14,BameClientConfig.ambientAnimation).render(c,mx,my,delta);
        String[] labels={"Intensity","Opacity","Radius","Animation Speed"};
        for(int i=0;i<4;i++) {
            int yy=sliderY(i),sx=sliderX(),sw=sliderWidth();
            text(c,labels[i],sx,yy,0xFFD4D8E0);
            CustomGuiUtils.fillUltraRounded(c,sx,yy+20,sw,4,0xFF303442,2);
            int fill=Math.round(sw*value(i));
            if(fill>0) CustomGuiUtils.fillUltraRounded(c,sx,yy+20,fill,4,GuiTheme.accent(),2);
            CustomGuiUtils.fillUltraRounded(c,sx+fill-3,yy+18,7,8,0xFFFFFFFF,4);
            text(c,Math.round(value(i)*100)+"%",sx+sw+9,yy+17,0xFFD4D8E0);
        }
        if(colorPicker!=null) {
            CustomGuiUtils.fillUltraRounded(c,colorPicker.x-7,colorPicker.y-6,colorPicker.width+14,108,0xFF0B0E16,5);
            colorPicker.render(c);
        }
    }
    boolean click(double mx,double my) {
        if(colorPicker!=null&&colorPicker.click(mx,my)) return true;
        for(int i=0;i<3;i++) if(hit(mx,my,x+half()-34,colorY(i),22,22)) {
            if(colorPicker!=null) colorPicker.release();
            if(open==i) { open=-1; colorPicker=null; return true; }
            open=i;
            colorPicker=switch(i) {
                case 0 -> new OutlineColorPicker(GuiTheme::accent,c->{ BameClientConfig.mainColor=c; BameClientConfig.customMainColor=true; BameClientConfig.save(); });
                case 1 -> new OutlineColorPicker(()->BameClientConfig.ambientPrimary,c->{ BameClientConfig.ambientPrimary=c; if(BameClientConfig.ambientMode==0) BameClientConfig.ambientMode=1; BameClientConfig.save(); });
                default -> new OutlineColorPicker(()->BameClientConfig.ambientSecondary,c->{ BameClientConfig.ambientSecondary=c; BameClientConfig.ambientMode=2; BameClientConfig.save(); });
            };
            layout(x,y,w); return true;
        }
        for(int group=0;group<2;group++) {
            int yy=groupY(group),count=group==0?2:3,ww=(half()-24)/count;
            for(int i=0;i<count;i++) if(hit(mx,my,x+12+i*ww,yy+15,ww-3,22)) {
                if(group==0) BameClientConfig.classicToggle=i==1; else BameClientConfig.ambientMode=i;
                BameClientConfig.save(); return true;
            }
        }
        for(int i=0;i<3;i++) if(hit(mx,my,right()+8,y+36+i*40,w-half()-20,32)) {
            switch(i) { case 0 -> BameClientConfig.seeThrough=!BameClientConfig.seeThrough;
                case 1 -> BameClientConfig.frostedBlur=!BameClientConfig.frostedBlur;
                case 2 -> BameClientConfig.ambientBackground=!BameClientConfig.ambientBackground; }
            BameClientConfig.save(); return true;
        }
        if(hit(mx,my,x+half()-38,motionTop()+54,26,14)) { BameClientConfig.ambientAnimation=!BameClientConfig.ambientAnimation; BameClientConfig.save(); return true; }
        for(int i=0;i<4;i++) if(hit(mx,my,sliderX()-4,sliderY(i)+12,sliderWidth()+8,22)) { slider=i; drag(mx,my); return true; }
        return false;
    }
    boolean dragging() { return slider>=0||(colorPicker!=null&&colorPicker.dragging()); }
    void drag(double mx,double my) {
        if(colorPicker!=null&&colorPicker.dragging()) { colorPicker.update(mx,my); return; }
        if(slider<0) return;
        float v=(float)Math.clamp((mx-sliderX())/sliderWidth(),0,1);
        switch(slider) { case 0 -> BameClientConfig.ambientIntensity=v; case 1 -> BameClientConfig.ambientOpacity=v;
            case 2 -> BameClientConfig.ambientRadius=v; case 3 -> BameClientConfig.animationSpeed=v; }
    }
    void release() {
        if(colorPicker!=null) colorPicker.release();
        if(slider>=0) slider=-1;
        BameClientConfig.save();
    }
    void close() {
        release();
        open=-1;
        colorPicker=null;
        BameClientConfig.save();
    }
}
