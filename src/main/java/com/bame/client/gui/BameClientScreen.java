package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import com.bame.client.module.AutoAreaMinerModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public class BameClientScreen extends Screen {
    private static final String[] CATEGORIES={"Combat","Movement","Visuals","Misc","World"};
    private String selected="World";
    private boolean expanded,listening,scrollDragging;
    private double scroll,scrollGrab;
    private int px,py,pw,ph,sidebar,cx,cy,cw,ch;
    private CustomTextFieldWidget search,corner1,corner2;
    private final OutlineColorPicker picker=new OutlineColorPicker();
    private final ThemeSettingsPanel themeSettings=new ThemeSettingsPanel();
    private final AmbientLighting ambient=new AmbientLighting();

    public BameClientScreen() { super(Text.literal("BameClient")); }
    private void text(DrawContext c,String s,int x,int y,int color) {
        c.drawText(textRenderer,CustomGuiUtils.getFontText(s),x,y,color,false);
    }
    private void box(DrawContext c,int x,int y,int w,int h,int color) {
        CustomGuiUtils.fillUltraRounded(c,x,y,w,h,color,6);
    }
    private boolean inside(double mx,double my,int x,int y,int w,int h) {
        return mx>=x&&mx<x+w&&my>=y&&my<y+h;
    }
    private void geometry() {
        pw=Math.min(width-16,Math.max(500,(int)(width*.62)));
        ph=Math.min(height-16,Math.max(340,(int)(height*.56)));
        px=(width-pw)/2; py=(height-ph)/2; sidebar=Math.max(110,pw/4);
        cx=px+sidebar+14; cy=py+58; cw=pw-sidebar-30; ch=ph-72;
    }
    @Override protected void init() {
        geometry();
        search=new CustomTextFieldWidget(px+pw-192,py+14,174,24,Text.literal("Search"));
        search.setPlaceholder("Search modules..."); search.setDrawsBackground(false);
        addSelectableChild(search);
        corner1=new CustomTextFieldWidget(0,0,cw-24,22,Text.literal("Corner 1"));
        corner2=new CustomTextFieldWidget(0,0,cw-24,22,Text.literal("Corner 2"));
        corner1.setText(format(AutoAreaMinerModule.corner1)); corner2.setText(format(AutoAreaMinerModule.corner2));
        corner1.setPlaceholder("X Y Z"); corner2.setPlaceholder("X Y Z");
        corner1.setChangedListener(s->AutoAreaMinerModule.corner1=parse(s));
        corner2.setChangedListener(s->AutoAreaMinerModule.corner2=parse(s));
        addSelectableChild(corner1); addSelectableChild(corner2); layout();
    }
    private boolean moduleVisible() {
        return selected.equals("World")&&(search.getText().isEmpty()||"auto area miner".contains(search.getText().toLowerCase(java.util.Locale.ROOT)));
    }
    private int columns() { return 4; }
    private int effectsY() { return 44+((GuiTheme.PRESETS.length+columns()-1)/columns())*58+18; }
    private int settingsY() { return effectsY(); }
    private int contentHeight() { return selected.equals("Theme")?settingsY()+themeSettings.height()+8:moduleVisible()?(expanded?336:46):0; }
    private double maxScroll() { return Math.max(0,contentHeight()-ch); }
    private int baseY() { return cy-(int)scroll; }
    private void layout() {
        scroll=Math.clamp(scroll,0,maxScroll());
        int y=baseY();
        corner1.setX(cx+12); corner1.setY(y+64);
        corner2.setX(cx+12); corner2.setY(y+104);
        corner1.visible=corner2.visible=moduleVisible()&&expanded;
        corner1.active=corner1.visible && corner1.getY()+22>cy && corner1.getY()<cy+ch;
        corner2.active=corner2.visible && corner2.getY()+22>cy && corner2.getY()<cy+ch;
        if(!corner1.active) corner1.setFocused(false);
        if(!corner2.active) corner2.setFocused(false);
        if(!corner1.visible) { corner1.setFocused(false); corner2.setFocused(false); }
        picker.layout(cx+14,y+228,Math.min(280,cw-28));
        themeSettings.layout(cx,y+settingsY(),cw);
    }
    private static String format(BlockPos p) { return p==null?"":p.getX()+" "+p.getY()+" "+p.getZ(); }
    private static BlockPos parse(String s) {
        try { var a=s.trim().split("\\s+"); if(a.length==3) return new BlockPos(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2])); }
        catch(NumberFormatException ignored) {} return null;
    }
    private String keyName() {
        if(listening) return "...";
        if(AutoAreaMinerModule.keyBind<0) return "None";
        String s=GLFW.glfwGetKeyName(AutoAreaMinerModule.keyBind,0);
        return s==null?"Key "+AutoAreaMinerModule.keyBind:s.toUpperCase(java.util.Locale.ROOT);
    }
    private void button(DrawContext c,String label,int x,int y,int w,int h,int mx,int my) {
        boolean hover=inside(mx,my,x,y,w,h);
        box(c,x,y,w,h,hover?0xFF252A34:0xFF181C24);
        CustomGuiUtils.drawUltraRoundedOutline(c,x,y,w,h,hover?GuiTheme.accent():0xFF292D36,4);
        int tw=textRenderer.getWidth(CustomGuiUtils.getFontText(label));
        text(c,label,x+(w-tw)/2,y+(h-8)/2,0xFFD4D8E0);
    }
    private void toggle(DrawContext c,int x,int y,boolean on,int mx,int my,float delta) {
        new CustomToggleWidget(x,y,26,14,on).render(c,mx,my,delta);
    }
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta) {
        // This screen draws its own dimming; only the world behind it is blurred.
        if(BameClientConfig.frostedBlur) applyBlur(c);
    }
    @Override public void render(DrawContext c,int mx,int my,float delta) {
        layout();
        c.fill(0,0,width,height,BameClientConfig.seeThrough?0x44000000:0x88000000);
        int bg=GuiTheme.alpha(GuiTheme.current().background(),BameClientConfig.seeThrough?205:255);
        box(c,px,py,pw,ph,bg);
        CustomGuiUtils.drawUltraRoundedOutline(c,px,py,pw,ph,0xFF292D36);
        ambient.render(c,px+sidebar+1,py+1,pw-sidebar-9,ph-9);
        c.fill(px+sidebar,py+12,px+sidebar+1,py+ph-12,0xFF292D36);
        c.drawTexturedQuad(net.minecraft.util.Identifier.of("bameclient","icon.png"),px+14,py+15,24,24,0f,0f,1f,1f);
        text(c,"Bame",px+44,py+23,0xFFFFFFFF);
        text(c,"MODULES",px+14,py+56,0xFF7F8694);
        int step=Math.min(26,Math.max(17,(ph-165)/6));
        for(int i=0;i<CATEGORIES.length;i++) {
            int y=py+73+i*step; boolean active=selected.equals(CATEGORIES[i]);
            if(active) box(c,px+6,y-3,sidebar-12,22,GuiTheme.alpha(GuiTheme.accent(),40));
            int color=active?GuiTheme.accent():0xFFABB1BE;
            text(c,CATEGORIES[i],px+34,y+5,color);
            switch(i) {
                case 0 -> CustomGuiUtils.drawCombatIcon(c,px+15,y+2,color);
                case 1 -> CustomGuiUtils.drawMovementIcon(c,px+15,y+2,color);
                case 2 -> CustomGuiUtils.drawVisualsIcon(c,px+15,y+2,color);
                case 3 -> CustomGuiUtils.drawMiscIcon(c,px+15,y+2,color);
                case 4 -> CustomGuiUtils.drawGlobeIcon(c,px+15,y+2,color);
            }
        }
        int gy=py+73+5*step+9;
        text(c,"GENERAL",px+14,gy,0xFF7F8694);
        if(selected.equals("Theme")) box(c,px+6,gy+16,sidebar-12,23,GuiTheme.alpha(GuiTheme.accent(),40));
        int tc=selected.equals("Theme")?GuiTheme.accent():0xFFABB1BE;
        CustomGuiUtils.drawSmoothRing(c,px+21,gy+27,5,1,tc);
        for(int i=0;i<3;i++) box(c,px+18+i*3,gy+24,2,2,tc);
        text(c,"Theme",px+34,gy+24,tc);
        int profileY=py+ph-40;
        box(c,px+8,profileY,sidebar-16,30,0xFF14181F);
        if(client.player!=null) {
            net.minecraft.client.gui.PlayerSkinDrawer.draw(c,client.player.getSkin(),px+13,profileY+5,20);
            c.enableScissor(px+38,profileY,px+sidebar-12,profileY+30);
            text(c,client.player.getName().getString(),px+38,profileY+11,0xFFD4D8E0); c.disableScissor();
        }
        box(c,px+pw-210,py+14,196,24,0xFF12161C);
        CustomGuiUtils.drawUltraRoundedOutline(c,px+pw-210,py+14,196,24,search.isFocused()?GuiTheme.accent():0xFF292D36);
        CustomGuiUtils.drawSearchIcon(c,px+pw-204,py+20,0xFFABB1BE);
        if(cw>260) text(c,java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),cx,py+22,GuiTheme.accent());
        search.render(c,mx,my,delta);
        c.fill(cx,py+48,cx+cw,py+49,GuiTheme.alpha(GuiTheme.accent(),180));
        c.enableScissor(cx,cy,cx+cw,cy+ch);
        if(selected.equals("Theme")) renderTheme(c,mx,my,delta);
        else if(moduleVisible()) renderMiner(c,mx,my,delta);
        c.disableScissor();
        if(maxScroll()>0) {
            box(c,px+pw-10,cy,4,ch,0xFF272C35);
            box(c,px+pw-10,thumbY(),4,thumbHeight(),scrollDragging?0xFFFFFFFF:GuiTheme.accent());
        }
    }
    private void renderMiner(DrawContext c,int mx,int my,float delta) {
        int y=baseY();
        box(c,cx,y,cw,expanded?336:46,GuiTheme.alpha(GuiTheme.surface(),BameClientConfig.seeThrough?210:255));
        text(c,"Auto Area Miner",cx+12,y+12,0xFFE2E5ED);
        text(c,"KeyBind:",cx+12,y+29,0xFF8E95A4);
        button(c,keyName(),cx+60,y+25,48,16,mx,my);
        toggle(c,cx+cw-38,y+12,AutoAreaMinerModule.enabled,mx,my,delta);
        if(!expanded) return;
        c.fill(cx+8,y+46,cx+cw-8,y+47,0xFF292D36);
        text(c,"Corner 1",cx+12,y+52,0xFFABB1BE); corner1.render(c,mx,my,delta);
        text(c,"Corner 2",cx+12,y+92,0xFFABB1BE); corner2.render(c,mx,my,delta);
        text(c,"Corner 1 - Feet",cx+12,y+138,0xFFABB1BE);
        text(c,"Corner 2 - Looking",cx+12,y+162,0xFFABB1BE);
        button(c,"Set",cx+cw-50,y+132,38,20,mx,my);
        button(c,"Set",cx+cw-50,y+156,38,20,mx,my);
        button(c,AutoAreaMinerModule.mode3x3?"Mode: 3x3 Pickaxe":"Mode: Normal Pickaxe",cx+12,y+184,cw-24,22,mx,my);
        text(c,"Outline Color",cx+14,y+214,0xFFD4D8E0);
        int swatchX=cx+cw-30;
        box(c,swatchX,y+211,16,12,0xFF777777);
        box(c,swatchX,y+211,16,12,BameClientConfig.outlineColor);
        picker.render(c);
    }
    private void renderTheme(DrawContext c,int mx,int my,float delta) {
        int y=baseY(),cols=columns(),tileW=(cw-8*(cols-1))/cols;
        text(c,"PRESETS",cx+4,y+4,0xFFE2E5ED);
        text(c,"Choose a palette for your client",cx+4,y+23,0xFF8E95A4);
        for(int i=0;i<GuiTheme.PRESETS.length;i++) {
            var p=GuiTheme.PRESETS[i]; int x=cx+(i%cols)*(tileW+8),ty=y+44+(i/cols)*58;
            box(c,x,ty,tileW,50,0xFF151A21);
            if(!BameClientConfig.customMainColor && p.name().equals(GuiTheme.current().name())) CustomGuiUtils.drawUltraRoundedOutline(c,x,ty,tileW,50,GuiTheme.accent(),6);
            c.enableScissor(x+4,ty,x+tileW-4,ty+23);
            text(c,p.name(),x+7,ty+7,0xFFCCD2DD);
            c.disableScissor();
            int[] colors={p.primary(),p.secondary(),p.highlight(),p.background()};
            for(int j=0;j<4;j++) c.fill(x+6+j*(tileW-12)/4,ty+23,x+6+(j+1)*(tileW-12)/4,ty+43,0xFF000000|colors[j]);
        }
        themeSettings.render(c,mx,my,delta);
    }
    private int thumbHeight() { return Math.max(24,(int)(ch*(ch/(double)Math.max(ch,contentHeight())))); }
    private int thumbY() { return cy+(int)((ch-thumbHeight())*(scroll/Math.max(1,maxScroll()))); }
    private void unfocus() { search.setFocused(false); corner1.setFocused(false); corner2.setFocused(false); setFocused(null); }
    @Override public boolean mouseClicked(Click click,boolean twice) {
        layout(); double mx=click.x(),my=click.y();
        if(click.button()!=0) return false;
        picker.release();
        int step=Math.min(26,Math.max(17,(ph-165)/6));
        for(int i=0;i<CATEGORIES.length;i++) if(inside(mx,my,px+6,py+70+i*step,sidebar-12,22)) { select(CATEGORIES[i]); return true; }
        int gy=py+73+5*step+9;
        if(inside(mx,my,px+6,gy+16,sidebar-12,23)) { select("Theme"); return true; }
        if(maxScroll()>0&&inside(mx,my,px+pw-14,cy,12,ch)) {
            unfocus(); scrollDragging=true;
            scrollGrab=inside(mx,my,px+pw-14,thumbY(),12,thumbHeight())?my-thumbY():thumbHeight()/2.0;
            dragScroll(my); return true;
        }
        unfocus();
        if(search.mouseClicked(click,twice)) { setFocused(search); return true; }
        if(!inside(mx,my,cx,cy,cw,ch)) return false;
        int y=baseY();
        if(selected.equals("Theme")) {
            int cols=columns(),tw=(cw-8*(cols-1))/cols;
            for(int i=0;i<GuiTheme.PRESETS.length;i++) if(inside(mx,my,cx+i%cols*(tw+8),y+44+i/cols*58,tw,50)) {
                BameClientConfig.theme=GuiTheme.PRESETS[i].name(); BameClientConfig.customMainColor=false; themeSettings.close(); BameClientConfig.save(); return true;
            }
            if(themeSettings.click(mx,my)) { layout(); return true; }
        } else if(moduleVisible()) {
            if(inside(mx,my,cx+cw-38,y+12,26,14)) { AutoAreaMinerModule.enabled=!AutoAreaMinerModule.enabled; return true; }
            if(inside(mx,my,cx+60,y+25,48,16)) { listening=true; return true; }
            if(inside(mx,my,cx,y,cw,46)) { expanded=!expanded; layout(); return true; }
            if(expanded) {
                if(corner1.mouseClicked(click,twice)) { setFocused(corner1); return true; }
                if(corner2.mouseClicked(click,twice)) { setFocused(corner2); return true; }
                if(inside(mx,my,cx+cw-50,y+132,38,20)) { setCorner(true); return true; }
                if(inside(mx,my,cx+cw-50,y+156,38,20)) { setCorner(false); return true; }
                if(inside(mx,my,cx+12,y+184,cw-24,22)) { AutoAreaMinerModule.mode3x3=!AutoAreaMinerModule.mode3x3; BameClientConfig.save(); return true; }
                if(picker.click(mx,my)) return true;
            }
        }
        return false;
    }
    private void select(String category) { picker.release(); themeSettings.close(); selected=category; scroll=0; listening=false; unfocus(); layout(); }
    private void setCorner(boolean first) {
        if(client.player==null||!(client.crosshairTarget instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK) return;
        BlockPos p=hit.getBlockPos();
        if(first) corner1.setText(format(new BlockPos(p.getX(),client.player.getBlockY(),p.getZ())));
        else corner2.setText(format(p));
    }
    private void dragScroll(double my) { scroll=Math.clamp((my-cy-scrollGrab)/Math.max(1,ch-thumbHeight())*maxScroll(),0,maxScroll()); layout(); }
    @Override public boolean mouseDragged(Click click,double dx,double dy) {
        if(themeSettings.dragging()) { themeSettings.drag(click.x(),click.y()); return true; }
        if(scrollDragging) { dragScroll(click.y()); return true; }
        if(picker.dragging()) { picker.update(click.x(),click.y()); return true; }
        return super.mouseDragged(click,dx,dy);
    }
    @Override public boolean mouseReleased(Click click) {
        boolean handled=scrollDragging||picker.dragging()||themeSettings.dragging(); scrollDragging=false; picker.release(); themeSettings.release();
        return handled||super.mouseReleased(click);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        if(inside(mx,my,cx,cy,cw+14,ch)&&!picker.dragging()&&!themeSettings.dragging()) { scroll=Math.clamp(scroll-vertical*26,0,maxScroll()); layout(); return true; }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyInput input) {
        if(input.key()==GLFW.GLFW_KEY_ESCAPE && !listening) themeSettings.close();
        if(listening) { AutoAreaMinerModule.keyBind=input.key()==GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listening=false; BameClientConfig.save(); return true; }
        return super.keyPressed(input);
    }
    @Override public void removed() { picker.release(); themeSettings.close(); BameClientConfig.save(); super.removed(); }
    @Override public boolean shouldPause() { return false; }
}
