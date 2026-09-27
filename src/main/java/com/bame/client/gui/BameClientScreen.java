package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import com.bame.client.module.AutoAreaMinerModule;
import com.bame.client.module.FullbrightModule;
import com.bame.client.module.KeyStrokesModule;
import com.bame.client.module.ZoomModule;
import com.bame.client.module.FpsModule;
import com.bame.client.module.PingModule;
import com.bame.client.module.CpsModule;

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
    public static String selected="World";
    private boolean expanded,listening,scrollDragging,draggingWidth,fullbrightExpanded,draggingFullbright,listeningFullbright,listeningMenuBind,listeningKeyStrokes,keyStrokesExpanded,listeningZoom,listeningFps,listeningPing,listeningCps;
    private boolean fpsExpanded,pingExpanded,cpsExpanded;
    private long resetClickedTime = 0;
    private double scroll,scrollGrab;
    private int px,py,pw,ph,sidebar,cx,cy,cw,ch;
    private CustomTextFieldWidget search,corner1,corner2;
    private long openTime=0;
    private final OutlineColorPicker picker=new OutlineColorPicker();
    private final ThemeSettingsPanel themeSettings=new ThemeSettingsPanel();

    private final OutlineColorPicker ksPicker = new OutlineColorPicker(() -> KeyStrokesModule.bgColor, c -> KeyStrokesModule.bgColor = c);
    private final OutlineColorPicker fpsPicker = new OutlineColorPicker(() -> FpsModule.bgColor, c -> FpsModule.bgColor = c);
    private final OutlineColorPicker pingPicker = new OutlineColorPicker(() -> PingModule.bgColor, c -> PingModule.bgColor = c);
    private final OutlineColorPicker cpsPicker = new OutlineColorPicker(() -> CpsModule.bgColor, c -> CpsModule.bgColor = c);

    private boolean ksPickerOpen = false;
    private boolean fpsPickerOpen = false;
    private boolean pingPickerOpen = false;
    private boolean cpsPickerOpen = false;

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
        pw = Math.min(width - 40, Math.max(640, (int)(width * 0.72)));
        ph = Math.min(height - 40, Math.max(400, (int)(height * 0.65)));
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sidebar = 130;
        cx = px + sidebar + 16;
        cy = py + 58;
        cw = pw - sidebar - 32;
        ch = ph - 72;
    }
    @Override protected void init() {
        if (openTime == 0) openTime = System.currentTimeMillis();
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
    private boolean isVisible(String moduleName, String category) {
        String q = search.getText().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) return moduleName.toLowerCase(java.util.Locale.ROOT).contains(q);
        return selected.equals(category);
    }
    private boolean fullbrightVisible() { return isVisible("Fullbright", "Visuals"); }
    private boolean minerVisible() { return isVisible("Auto Area Miner", "World"); }
    private boolean keyStrokesVisible() { return isVisible("KeyStrokes", "Visuals"); }
    private boolean zoomVisible() { return isVisible("Zoom", "Visuals"); }

    private boolean fpsVisible() { return isVisible("FPS", "Visuals"); }
    private boolean pingVisible() { return isVisible("Ping", "Visuals"); }
    private boolean cpsVisible() { return isVisible("CPS", "Visuals"); }

    private int columns() { return 4; }
    private int effectsY() { return 44+((GuiTheme.PRESETS.length+columns()-1)/columns())*58+18; }
    private int settingsY() { return effectsY(); }

    private int getModuleHeight(boolean expanded, int bgMode, boolean pickerOpen) {
        if (!expanded) return 46;
        if (bgMode == 2 && pickerOpen) return 184;
        return 108;
    }

    private int contentHeight() { 
        if (selected.equals("Theme")) return settingsY()+themeSettings.height()+8;
        if (selected.equals("Settings")) return 104;
        int leftY = 0;
        if (minerVisible()) leftY += (expanded?346:46) + 12;
        int rightY = leftY;
        if (fullbrightVisible()) leftY += (fullbrightExpanded?92:46) + 12;
        if (zoomVisible()) leftY += (ZoomModule.expanded?92:46) + 12;
        if (pingVisible()) leftY += getModuleHeight(pingExpanded, PingModule.bgMode, pingPickerOpen) + 12;

        if (keyStrokesVisible()) rightY += getModuleHeight(keyStrokesExpanded, KeyStrokesModule.bgMode, ksPickerOpen) + 12;
        if (fpsVisible()) rightY += getModuleHeight(fpsExpanded, FpsModule.bgMode, fpsPickerOpen) + 12;
        if (cpsVisible()) rightY += getModuleHeight(cpsExpanded, CpsModule.bgMode, cpsPickerOpen) + 12;
        return Math.max(leftY, rightY);
    }
    private double maxScroll() { return Math.max(0,contentHeight()-ch); }
    private int baseY() { return cy-(int)scroll; }
    private void layout() {
        scroll=Math.clamp(scroll,0,maxScroll());
        int yOffset = 0;
        int leftY = baseY();
        if(minerVisible()) {
            corner1.setX(cx+12); corner1.setY(leftY+64);
            corner2.setX(cx+12); corner2.setY(leftY+104);
            corner1.visible=corner2.visible=expanded;
            corner1.active=corner1.visible && corner1.getY()+22>cy && corner1.getY()<cy+ch;
            corner2.active=corner2.visible && corner2.getY()+22>cy && corner2.getY()<cy+ch;
            if(!corner1.active) corner1.setFocused(false);
            if(!corner2.active) corner2.setFocused(false);
            if(!corner1.visible) { corner1.setFocused(false); corner2.setFocused(false); }
            picker.layout(cx+14,leftY+228,140);
        } else {
            corner1.visible=corner2.visible=false;
            corner1.active=corner2.active=false;
            corner1.setFocused(false); corner2.setFocused(false);
        }
        themeSettings.layout(cx,baseY()+settingsY(),cw);
    }
    private static String format(BlockPos p) { return p==null?"":p.getX()+" "+p.getY()+" "+p.getZ(); }
    private static BlockPos parse(String s) {
        try { var a=s.trim().split("\\s+"); if(a.length==3) return new BlockPos(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2])); }
        catch(NumberFormatException ignored) {} return null;
    }
    private String formatKey(int key) {
        if(key<0) return "None";
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) return "RSHIFT";
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) return "LSHIFT";
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) return "RCTRL";
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) return "LCTRL";
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) return "RALT";
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) return "LALT";
        String s = org.lwjgl.glfw.GLFW.glfwGetKeyName(key,0);
        if(s!=null) return s.toUpperCase(java.util.Locale.ROOT);
        return net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString().toUpperCase(java.util.Locale.ROOT);
    }

    
    private String animName() {
        int a = com.bame.client.BameClientConfig.guiAnimation;
        return a == 0 ? "Fade In" : (a == 1 ? "Slide In" : "None");
    }

    private String keyNameMenuBind() {
        if(listeningMenuBind) return "...";
        return formatKey(com.bame.client.BameClientConfig.menuBind);
    }
    private String keyNameKeyStrokes() {
        if(listeningKeyStrokes) return "...";
        return formatKey(com.bame.client.module.KeyStrokesModule.keyBind);
    }
    private String keyNameFullbright() {
        if(listeningFullbright) return "...";
        return formatKey(com.bame.client.module.FullbrightModule.keyBind);
    }
    private String keyName() {
        if(listening) return "...";
        if(AutoAreaMinerModule.keyBind<0) return "None";
        String s=GLFW.glfwGetKeyName(AutoAreaMinerModule.keyBind,0);
        return s==null?"Key "+AutoAreaMinerModule.keyBind:s.toUpperCase(java.util.Locale.ROOT);
    }
    private void modeButton(DrawContext c,String label,int x,int y,int w,int h,int mx,int my,boolean selected) {
        boolean hover=inside(mx,my,x,y,w,h);
        box(c,x,y,w,h,selected?GuiTheme.alpha(GuiTheme.accent(), 80):(hover?0xFF252A34:0xFF181C24));
        CustomGuiUtils.drawUltraRoundedOutline(c,x,y,w,h,selected?GuiTheme.accent():(hover?0xFF606575:0xFF292D36),4);
        int tw=textRenderer.getWidth(CustomGuiUtils.getFontText(label));
        text(c,label,x+(w-tw)/2,y+(h-8)/2,selected?0xFFFFFFFF:0xFFD4D8E0);
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
        float progress = Math.min(1.0f, (System.currentTimeMillis() - openTime) / 400.0f);
        float p = 1.0f - (float)Math.pow(1.0f - progress, 4);
        int anim = com.bame.client.BameClientConfig.guiAnimation;
        if (progress < 1.0f) {
            
        }
        
        int dimAlpha = (int)((BameClientConfig.seeThrough?0x44:0x88) * p);
        c.fill(0,0,width,height, (dimAlpha << 24) | 0x000000);
        
        if (progress < 1.0f && anim != 2) {
            c.getMatrices().pushMatrix();
            if (anim == 0) {
                c.getMatrices().translate((float)width/2f, (float)height/2f);
                float s = 0.6f + 0.4f * p;
                c.getMatrices().scale(s, s);
                c.getMatrices().translate(-(float)width/2f, -(float)height/2f);
                
            } else if (anim == 1) {
                c.getMatrices().translate(0f, (float)height * (1.0f - p));
            }
        }

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
        
        if(selected.equals("Settings")) box(c,px+6,gy+39,sidebar-12,23,GuiTheme.alpha(GuiTheme.accent(),40));
        int sc=selected.equals("Settings")?GuiTheme.accent():0xFFABB1BE;
        CustomGuiUtils.drawGearIcon(c,px+15,gy+44,sc);
        text(c,"Settings",px+34,gy+47,sc);
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
        else if(selected.equals("Settings")) renderSettings(c,mx,my,delta);
        else {
            int leftY = 0;
            if (minerVisible()) {
                renderMiner(c,mx,my,delta,leftY);
                leftY += (expanded?346:46) + 12;
            }
            int rightY = leftY;
            int gap = 16;
            int halfW = (cw - gap) / 2;
            if (fullbrightVisible()) {
                renderFullbright(c, mx, my, delta, cx, leftY, halfW);
                leftY += (fullbrightExpanded?92:46) + 12;
            }
            if (zoomVisible()) {
                renderZoom(c, mx, my, delta, cx, leftY, halfW);
                leftY += (ZoomModule.expanded?92:46) + 12;
            }
            if (pingVisible()) {
                renderPingModule(c, mx, my, delta, cx, leftY, halfW);
                leftY += getModuleHeight(pingExpanded, PingModule.bgMode, pingPickerOpen) + 12;
            }

            if (keyStrokesVisible()) {
                renderKeyStrokes(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += getModuleHeight(keyStrokesExpanded, KeyStrokesModule.bgMode, ksPickerOpen) + 12;
            }
            if (fpsVisible()) {
                renderFpsModule(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += getModuleHeight(fpsExpanded, FpsModule.bgMode, fpsPickerOpen) + 12;
            }
            if (cpsVisible()) {
                renderCpsModule(c, mx, my, delta, cx + halfW + gap, rightY, halfW);
                rightY += getModuleHeight(cpsExpanded, CpsModule.bgMode, cpsPickerOpen) + 12;
            }
        }
        c.disableScissor();
        if(maxScroll()>0) {
            box(c,px+pw-10,cy,4,ch,0xFF272C35);
            box(c,px+pw-10,thumbY(),4,thumbHeight(),scrollDragging?0xFFFFFFFF:GuiTheme.accent());
        }
        
        if (progress < 1.0f && anim != 2) {
            c.getMatrices().popMatrix();
            
        }
    }
    private void renderMiner(DrawContext c,int mx,int my,float delta, int yOffset) {
        int y=baseY() + yOffset;
        box(c,cx,y,cw,expanded?346:46,GuiTheme.alpha(GuiTheme.surface(),BameClientConfig.seeThrough?210:255));
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
        text(c,"Style",cx+170,y+214,0xFFD4D8E0);
        int mode = BameClientConfig.renderMode;
        modeButton(c,"Clean",cx+170,y+228,45,18,mx,my,mode==0);
        modeButton(c,"Outline",cx+220,y+228,55,18,mx,my,mode==1);
        modeButton(c,"Corners",cx+170,y+250,55,18,mx,my,mode==2);
        modeButton(c,"Pulse",cx+230,y+250,45,18,mx,my,mode==3);
        
        picker.render(c);

        int sliderY = y+304;
        text(c,"Outline Width",cx+14,sliderY,0xFFD4D8E0);
        int sw = 140; // width of slider
        CustomGuiUtils.fillUltraRounded(c,cx+14,sliderY+16,sw,4,0xFF303442,2);
        float widthVal = (BameClientConfig.outlineWidth - 1.0f) / 4.0f; // 0 to 1
        int fill = Math.round(sw * widthVal);
        if(fill>0) CustomGuiUtils.fillUltraRounded(c,cx+14,sliderY+16,fill,4,GuiTheme.accent(),2);
        CustomGuiUtils.fillUltraRounded(c,cx+14+fill-3,sliderY+14,7,8,0xFFFFFFFF,4);
        text(c,String.format("%.1f", BameClientConfig.outlineWidth),cx+14+sw+8,sliderY+14,0xFFD4D8E0);
    }
    private void renderFullbright(DrawContext c,int mx,int my,float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        box(c,x,y,w,fullbrightExpanded?92:46,GuiTheme.alpha(GuiTheme.surface(),com.bame.client.BameClientConfig.seeThrough?210:255));
        text(c,"Fullbright",x+12,y+12,0xFFE2E5ED);
        text(c,"KeyBind:",x+12,y+29,0xFF8E95A4);
        button(c,keyNameFullbright(),x+60,y+25,48,16,mx,my);
        toggle(c,x+w-38,y+12,com.bame.client.module.FullbrightModule.enabled,mx,my,delta);
        
        if (!fullbrightExpanded) return;
        c.fill(x+8,y+46,x+w-8,y+47,0xFF292D36);
        
        int sliderY = y+58;
        text(c,"Intensity",x+12,sliderY,0xFFD4D8E0);
        
        int sx = x+12;
        int sw = w-24;
        CustomGuiUtils.fillUltraRounded(c,sx,sliderY+20,sw,4,0xFF303442,2);
        
        float val = com.bame.client.module.FullbrightModule.intensity;
        int fill = Math.round(sw * val);
        if(fill>0) CustomGuiUtils.fillUltraRounded(c,sx,sliderY+20,fill,4,GuiTheme.accent(),2);
        CustomGuiUtils.fillUltraRounded(c,sx+fill-3,sliderY+18,7,8,0xFFFFFFFF,4);
        
        text(c,Math.round(val*100)+"%",sx+w-38,sliderY+8,0xFFD4D8E0);
    }
    
    private void renderKeyStrokes(DrawContext c,int mx,int my,float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getModuleHeight(keyStrokesExpanded, KeyStrokesModule.bgMode, ksPickerOpen);
        box(c,x,y,w,h,GuiTheme.alpha(GuiTheme.surface(),BameClientConfig.seeThrough?210:255));
        text(c,"KeyStrokes",x+12,y+12,0xFFE2E5ED);
        text(c,"KeyBind:",x+12,y+29,0xFF8E95A4);
        button(c,keyNameKeyStrokes(),x+60,y+25,48,16,mx,my);
        toggle(c,x+w-38,y+12,KeyStrokesModule.enabled,mx,my,delta);
        
        if (!keyStrokesExpanded) return;
        c.fill(x+8,y+46,x+w-8,y+47,0xFF292D36);
        
        button(c, "Edit HUD", x + 12, y+58, 60, 20, mx, my);
        
        long timeSince = System.currentTimeMillis() - resetClickedTime;
        float s = 1.0f;
        if (timeSince < 150) {
            float p = (float)timeSince / 150f;
            if (p < 0.5f) {
                s = 1.0f - (p * 2f) * 0.25f;
            } else {
                s = 0.75f + ((p - 0.5f) * 2f) * 0.25f;
            }
        }
        
        c.getMatrices().pushMatrix();
        int iconX = x + w - 33;
        int iconY = y + 60;
        c.getMatrices().translate((float)(iconX + 8), (float)(iconY + 8));
        c.getMatrices().scale(s, s);
        c.getMatrices().translate((float)-(iconX + 8), (float)-(iconY + 8));
        CustomGuiUtils.drawResetIcon(c, iconX, iconY, 0xFFFFFFFF);
        c.getMatrices().popMatrix();

        renderBgSelector(c, mx, my, x, y + 84, w, KeyStrokesModule.bgMode, KeyStrokesModule.bgColor, ksPickerOpen, ksPicker);
    }
    
    
    
    private void renderBgSelector(DrawContext c, int mx, int my, int x, int y, int w, int mode, int color, boolean pickerOpen, OutlineColorPicker picker) {
        text(c, "BG", x + 12, y + 3, 0xFF8E95A4);
        int bx = x + 36;
        modeButton(c, "Dark", bx, y, 32, 16, mx, my, mode == 0);
        modeButton(c, "Clear", bx + 36, y, 34, 16, mx, my, mode == 1);
        modeButton(c, "Color", bx + 74, y, 34, 16, mx, my, mode == 2);
        modeButton(c, "Chroma", bx + 112, y, 42, 16, mx, my, mode == 3);

        if (mode == 2) {
            int sx = bx + 158;
            box(c, sx, y, 16, 16, 0xFF292D36);
            box(c, sx + 2, y + 2, 12, 12, color);
            CustomGuiUtils.drawUltraRoundedOutline(c, sx, y, 16, 16, pickerOpen ? GuiTheme.accent() : 0xFF555555, 3);
            if (pickerOpen) {
                picker.layout(x + 12, y + 22, w - 24);
                picker.render(c);
            }
        }
    }

    private void renderFpsModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getModuleHeight(fpsExpanded, FpsModule.bgMode, fpsPickerOpen);
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "FPS", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);
        String kb = listeningFps ? "..." : formatKey(FpsModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, FpsModule.enabled, mx, my, delta);

        if (!fpsExpanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);
        button(c, "Edit HUD", x + 12, y + 58, 60, 20, mx, my);

        renderBgSelector(c, mx, my, x, y + 84, w, FpsModule.bgMode, FpsModule.bgColor, fpsPickerOpen, fpsPicker);
    }

    private void renderPingModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getModuleHeight(pingExpanded, PingModule.bgMode, pingPickerOpen);
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "Ping", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);
        String kb = listeningPing ? "..." : formatKey(PingModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, PingModule.enabled, mx, my, delta);

        if (!pingExpanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);
        button(c, "Edit HUD", x + 12, y + 58, 60, 20, mx, my);

        renderBgSelector(c, mx, my, x, y + 84, w, PingModule.bgMode, PingModule.bgColor, pingPickerOpen, pingPicker);
    }

    private void renderCpsModule(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        int h = getModuleHeight(cpsExpanded, CpsModule.bgMode, cpsPickerOpen);
        box(c, x, y, w, h, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "CPS", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);
        String kb = listeningCps ? "..." : formatKey(CpsModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        toggle(c, x + w - 38, y + 12, CpsModule.enabled, mx, my, delta);

        if (!cpsExpanded) return;
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);
        button(c, "Edit HUD", x + 12, y + 58, 60, 20, mx, my);

        renderBgSelector(c, mx, my, x, y + 84, w, CpsModule.bgMode, CpsModule.bgColor, cpsPickerOpen, cpsPicker);
    }

    private void renderZoom(DrawContext c, int mx, int my, float delta, int x, int yOffset, int w) {
        int y = baseY() + yOffset;
        box(c, x, y, w, ZoomModule.expanded ? 92 : 46, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        text(c, "Zoom", x + 12, y + 12, 0xFFE2E5ED);
        text(c, "KeyBind:", x + 12, y + 29, 0xFF8E95A4);
        
        String kb = listeningZoom ? "..." : formatKey(ZoomModule.keyBind);
        button(c, kb, x + 60, y + 25, 48, 16, mx, my);
        
        toggle(c, x + w - 38, y + 12, ZoomModule.enabled, mx, my, delta);
        if (!ZoomModule.expanded) return;
        
        c.fill(x + 8, y + 46, x + w - 8, y + 47, 0xFF292D36);
        button(c, ZoomModule.mode == 0 ? "Smooth" : "Instant", x + 12, y + 58, 60, 20, mx, my);
    }

    private void renderVisualsRow(DrawContext c, int mx, int my, float delta, int yOffset) {
        int gap = 16;
        int halfW = (cw - gap) / 2;
        renderFullbright(c, mx, my, delta, cx, yOffset, halfW);
        renderKeyStrokes(c, mx, my, delta, cx + halfW + gap, yOffset, halfW);
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
    private void renderSettings(DrawContext c,int mx,int my,float delta) {
        int iy = baseY() + 12;
        CustomGuiUtils.fillUltraRounded(c,cx,iy,cw,80,0xE812161E,6);
        text(c,"INTERFACE",cx+12,iy+12,0xFFB5BCC9);
        text(c,"Menu Bind",cx+12,iy+34,0xFFD4D8E0);
        button(c,keyNameMenuBind(),cx+cw-72,iy+30,60,16,mx,my);
        text(c,"GUI Animation",cx+12,iy+58,0xFFD4D8E0);
        button(c,animName(),cx+cw-72,iy+54,60,16,mx,my);
    }
    private int thumbHeight() { return Math.max(24,(int)(ch*(ch/(double)Math.max(ch,contentHeight())))); }
    private int thumbY() { return cy+(int)((ch-thumbHeight())*(scroll/Math.max(1,maxScroll()))); }
    private void unfocus() { search.setFocused(false); corner1.setFocused(false); corner2.setFocused(false); setFocused(null); }
    @Override public boolean mouseClicked(Click click,boolean twice) {
        layout(); double mx=click.x(),my=click.y();
        if(click.button()!=0) return false;
        picker.release(); ksPicker.release(); fpsPicker.release(); pingPicker.release(); cpsPicker.release();
        int step=Math.min(26,Math.max(17,(ph-165)/6));
        for(int i=0;i<CATEGORIES.length;i++) if(inside(mx,my,px+6,py+70+i*step,sidebar-12,22)) { select(CATEGORIES[i]); return true; }
        int gy=py+73+5*step+9;
        if(inside(mx,my,px+6,gy+16,sidebar-12,23)) { select("Theme"); return true; }
        if(inside(mx,my,px+6,gy+39,sidebar-12,23)) { select("Settings"); return true; }
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
                com.bame.client.BameClientConfig.theme=GuiTheme.PRESETS[i].name(); com.bame.client.BameClientConfig.customMainColor=false; themeSettings.close(); com.bame.client.BameClientConfig.save(); return true;
            }
            if(themeSettings.click(mx,my)) { layout(); return true; }
        } else if(selected.equals("Settings")) {
            int iy = baseY() + 12;
            if(inside(mx,my,cx+cw-72,iy+30,60,16)) { listeningMenuBind = true; return true; }
            if(inside(mx,my,cx+cw-72,iy+54,60,16)) {
                com.bame.client.BameClientConfig.guiAnimation = (com.bame.client.BameClientConfig.guiAnimation + 1) % 3;
                com.bame.client.BameClientConfig.save();
                return true;
            }
        } else {
            int leftY = 0;
            if(minerVisible()) {
                int myY = baseY() + leftY;
                if(inside(mx,my,cx+cw-38,myY+12,26,14)) { com.bame.client.module.AutoAreaMinerModule.enabled=!com.bame.client.module.AutoAreaMinerModule.enabled; return true; }
                if(inside(mx,my,cx+60,myY+25,48,16)) { listening=true; return true; }
                if(inside(mx,my,cx,myY,cw,46)) { expanded=!expanded; layout(); return true; }
                if(expanded) {
                    if(corner1.mouseClicked(click,twice)) { setFocused(corner1); return true; }
                    if(corner2.mouseClicked(click,twice)) { setFocused(corner2); return true; }
                    if(inside(mx,my,cx+cw-50,myY+132,38,20)) { setCorner(true); return true; }
                    if(inside(mx,my,cx+cw-50,myY+156,38,20)) { setCorner(false); return true; }
                    if(inside(mx,my,cx+12,myY+184,cw-24,22)) { AutoAreaMinerModule.mode3x3=!AutoAreaMinerModule.mode3x3; return true; }
                    if(inside(mx,my,cx+170,myY+228,45,18)) { BameClientConfig.renderMode=0; return true; }
                    if(inside(mx,my,cx+220,myY+228,55,18)) { BameClientConfig.renderMode=1; return true; }
                    if(inside(mx,my,cx+170,myY+250,55,18)) { BameClientConfig.renderMode=2; return true; }
                    if(inside(mx,my,cx+230,myY+250,45,18)) { BameClientConfig.renderMode=3; return true; }
                    
                    if(picker.click(mx,my)) return true;
                }
                leftY += (expanded?346:46) + 12;
            }
            int rightY = leftY;
            int gap = 16;
            int halfW = (cw - gap) / 2;
            
            if(fullbrightVisible()) {
                int myY = baseY() + leftY;
                if(inside(mx,my,cx+halfW-38,myY+12,26,14)) { FullbrightModule.enabled=!FullbrightModule.enabled; BameClientConfig.save(); return true; }
                if(inside(mx,my,cx+60,myY+25,48,16)) { listeningFullbright=true; return true; }
                if(inside(mx,my,cx,myY,halfW,46)) { fullbrightExpanded=!fullbrightExpanded; layout(); return true; }
                leftY += (fullbrightExpanded?92:46) + 12;
            }
            
            if(zoomVisible()) {
                int myY = baseY() + leftY;
                if(inside(mx,my,cx+halfW-38,myY+12,26,14)) { ZoomModule.enabled=!ZoomModule.enabled; BameClientConfig.save(); return true; }
                if(inside(mx,my,cx+60,myY+25,48,16)) { listeningZoom=true; return true; }
                if(inside(mx,my,cx,myY,halfW,46)) { ZoomModule.expanded=!ZoomModule.expanded; layout(); return true; }
                if(ZoomModule.expanded) {
                    if(inside(mx,my,cx+12,myY+58,60,20)) {
                        ZoomModule.mode = (ZoomModule.mode == 0) ? 1 : 0;
                        BameClientConfig.save();
                        return true;
                    }
                }
                leftY += (ZoomModule.expanded?92:46) + 12;
            }
            if(pingVisible()) {
                int myY = baseY() + leftY;
                if(inside(mx,my,cx+halfW-38,myY+12,26,14)) { PingModule.enabled=!PingModule.enabled; BameClientConfig.save(); return true; }
                if(inside(mx,my,cx+60,myY+25,48,16)) { listeningPing=true; return true; }
                if(inside(mx,my,cx,myY,halfW,46)) { pingExpanded=!pingExpanded; layout(); return true; }
                if(pingExpanded) {
                    if(inside(mx,my,cx+12,myY+58,60,20)) { client.setScreen(new HudEditorScreen(this)); return true; }
                    int bx = cx + 36;
                    if(inside(mx,my,bx,myY+84,32,16)) { PingModule.bgMode=0; pingPickerOpen=false; BameClientConfig.save(); return true; }
                    if(inside(mx,my,bx+36,myY+84,34,16)) { PingModule.bgMode=1; pingPickerOpen=false; BameClientConfig.save(); return true; }
                    if(inside(mx,my,bx+74,myY+84,34,16)) { PingModule.bgMode=2; pingPickerOpen=true; BameClientConfig.save(); return true; }
                    if(inside(mx,my,bx+112,myY+84,42,16)) { PingModule.bgMode=3; pingPickerOpen=false; BameClientConfig.save(); return true; }
                    if(PingModule.bgMode==2 && inside(mx,my,bx+158,myY+84,16,16)) { pingPickerOpen=!pingPickerOpen; layout(); return true; }
                    if(PingModule.bgMode==2 && pingPickerOpen && pingPicker.click(mx,my)) return true;
                }
                leftY += getModuleHeight(pingExpanded, PingModule.bgMode, pingPickerOpen) + 12;
            }

            if(keyStrokesVisible()) {
                int myY = baseY() + rightY;
                int kX = cx + halfW + gap;
                if(inside(mx,my,kX+halfW-38,myY+12,26,14)) { KeyStrokesModule.enabled=!KeyStrokesModule.enabled; BameClientConfig.save(); return true; }
                if(inside(mx,my,kX+60,myY+25,48,16)) { listeningKeyStrokes=true; return true; }
                if(inside(mx,my,kX,myY,halfW,46)) { keyStrokesExpanded=!keyStrokesExpanded; layout(); return true; }
                if(keyStrokesExpanded) {
                    if(inside(mx,my,kX+12,myY+58,60,20)) { client.setScreen(new HudEditorScreen(this)); return true; }
                    if(inside(mx,my,kX+halfW-gap-40,myY+55,30,30)) {
                        resetClickedTime = System.currentTimeMillis();
                        KeyStrokesModule.resetKeys();
                        BameClientConfig.save();
                        return true;
                    }
                    int bx = kX + 36;
                    if(inside(mx,my,bx,myY+84,32,16)) { KeyStrokesModule.bgMode=0; ksPickerOpen=false; BameClientConfig.save(); return true; }
                    if(inside(mx,my,bx+36,myY+84,34,16)) { KeyStrokesModule.bgMode=1; ksPickerOpen=false; BameClientConfig.save(); return true; }
                    if(inside(mx,my,bx+74,myY+84,34,16)) { KeyStrokesModule.bgMode=2; ksPickerOpen=true; BameClientConfig.save(); return true; }
                    if(inside(mx,my,bx+112,myY+84,42,16)) { KeyStrokesModule.bgMode=3; ksPickerOpen=false; BameClientConfig.save(); return true; }
                    if(KeyStrokesModule.bgMode==2 && inside(mx,my,bx+158,myY+84,16,16)) { ksPickerOpen=!ksPickerOpen; layout(); return true; }
                    if(KeyStrokesModule.bgMode==2 && ksPickerOpen && ksPicker.click(mx,my)) return true;
                }
                rightY += getModuleHeight(keyStrokesExpanded, KeyStrokesModule.bgMode, ksPickerOpen) + 12;
            }
            if(fpsVisible()) {
                int myY = baseY() + rightY;
                int kX = cx + halfW + gap;
                if(inside(mx,my,kX+halfW-38,myY+12,26,14)) { FpsModule.enabled=!FpsModule.enabled; BameClientConfig.save(); return true; }
                if(inside(mx,my,kX+60,myY+25,48,16)) { listeningFps=true; return true; }
                if(inside(mx,my,kX,myY,halfW,46)) { fpsExpanded=!fpsExpanded; layout(); return true; }
                if(fpsExpanded) {
                    if(inside(mx,my,kX+12,myY+58,60,20)) { client.setScreen(new HudEditorScreen(this)); return true; }
                    int bx = kX + 36;
                    if(inside(mx,my,bx,myY+84,32,16)) { FpsModule.bgMode=0; fpsPickerOpen=false; BameClientConfig.save(); return true; }
                    if(inside(mx,my,bx+36,myY+84,34,16)) { FpsModule.bgMode=1; fpsPickerOpen=false; BameClientConfig.save(); return true; }
                    if(inside(mx,my,bx+74,myY+84,34,16)) { FpsModule.bgMode=2; fpsPickerOpen=true; BameClientConfig.save(); return true; }
                    if(inside(mx,my,bx+112,myY+84,42,16)) { FpsModule.bgMode=3; fpsPickerOpen=false; BameClientConfig.save(); return true; }
                    if(FpsModule.bgMode==2 && inside(mx,my,bx+158,myY+84,16,16)) { fpsPickerOpen=!fpsPickerOpen; layout(); return true; }
                    if(FpsModule.bgMode==2 && fpsPickerOpen && fpsPicker.click(mx,my)) return true;
                }
                rightY += getModuleHeight(fpsExpanded, FpsModule.bgMode, fpsPickerOpen) + 12;
            }
            if(cpsVisible()) {
                int myY = baseY() + rightY;
                int kX = cx + halfW + gap;
                if(inside(mx,my,kX+halfW-38,myY+12,26,14)) { CpsModule.enabled=!CpsModule.enabled; BameClientConfig.save(); return true; }
                if(inside(mx,my,kX+60,myY+25,48,16)) { listeningCps=true; return true; }
                if(inside(mx,my,kX,myY,halfW,46)) { cpsExpanded=!cpsExpanded; layout(); return true; }
                if(cpsExpanded) {
                    if(inside(mx,my,kX+12,myY+58,60,20)) { client.setScreen(new HudEditorScreen(this)); return true; }
                    int bx = kX + 36;
                    if(inside(mx,my,bx,myY+84,32,16)) { CpsModule.bgMode=0; cpsPickerOpen=false; BameClientConfig.save(); return true; }
                    if(inside(mx,my,bx+36,myY+84,34,16)) { CpsModule.bgMode=1; cpsPickerOpen=false; BameClientConfig.save(); return true; }
                    if(inside(mx,my,bx+74,myY+84,34,16)) { CpsModule.bgMode=2; cpsPickerOpen=true; BameClientConfig.save(); return true; }
                    if(inside(mx,my,bx+112,myY+84,42,16)) { CpsModule.bgMode=3; cpsPickerOpen=false; BameClientConfig.save(); return true; }
                    if(CpsModule.bgMode==2 && inside(mx,my,bx+158,myY+84,16,16)) { cpsPickerOpen=!cpsPickerOpen; layout(); return true; }
                    if(CpsModule.bgMode==2 && cpsPickerOpen && cpsPicker.click(mx,my)) return true;
                }
                rightY += getModuleHeight(cpsExpanded, CpsModule.bgMode, cpsPickerOpen) + 12;
            }
        }return false;
    }
    private void select(String category) { picker.release(); ksPicker.release(); fpsPicker.release(); pingPicker.release(); cpsPicker.release(); themeSettings.close(); selected=category; scroll=0; listening=false; listeningZoom=false; listeningFps=false; listeningPing=false; listeningCps=false; unfocus(); layout(); }
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
        if(draggingFullbright) {
            int gap = 16;
            int halfW = (cw - gap) / 2;
            int sx = cx+12; int sw = halfW-24;
            float fval = (float)Math.clamp(((click.x() - sx) / (double)sw), 0.0, 1.0);
            com.bame.client.module.FullbrightModule.intensity = fval;
            return true;
        }
        if(draggingWidth) {
            float val = (float)Math.clamp(((click.x() - (cx+14)) / 140.0), 0.0, 1.0);
            BameClientConfig.outlineWidth = 1.0f + val * 4.0f;
            return true;
        }
        if(picker.dragging()) { picker.update(click.x(),click.y()); return true; }
        return super.mouseDragged(click,dx,dy);
    }
    @Override public boolean mouseReleased(Click click) {
        boolean handled=scrollDragging||picker.dragging()||themeSettings.dragging()||draggingWidth||draggingFullbright;
        scrollDragging=false; draggingWidth=false; draggingFullbright=false; com.bame.client.BameClientConfig.save(); picker.release(); ksPicker.release(); fpsPicker.release(); pingPicker.release(); cpsPicker.release(); themeSettings.release();
        return handled||super.mouseReleased(click);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        if(inside(mx,my,cx,cy,cw+14,ch)&&!picker.dragging()&&!themeSettings.dragging()) { scroll=Math.clamp(scroll-vertical*26,0,maxScroll()); layout(); return true; }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyInput input) {
        if(input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && !listening && !listeningFullbright && !listeningMenuBind && !listeningKeyStrokes && !listeningZoom) themeSettings.close();
        if(listening) { AutoAreaMinerModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listening=false; BameClientConfig.save(); return true; }
        if(listeningMenuBind) { com.bame.client.BameClientConfig.menuBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningMenuBind=false; com.bame.client.BameClientConfig.save(); return true; }
        if(listeningFullbright) { com.bame.client.module.FullbrightModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningFullbright=false; BameClientConfig.save(); return true; }
        if(listeningKeyStrokes) { com.bame.client.module.KeyStrokesModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningKeyStrokes=false; BameClientConfig.save(); return true; }
        if(listeningZoom) { com.bame.client.module.ZoomModule.keyBind=input.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE?-1:input.key(); listeningZoom=false; listeningFps=false; listeningPing=false; listeningCps=false; BameClientConfig.save(); return true; }
        return super.keyPressed(input);
    }
    @Override public void removed() { picker.release(); ksPicker.release(); fpsPicker.release(); pingPicker.release(); cpsPicker.release(); themeSettings.close(); BameClientConfig.save(); super.removed(); }
    @Override public boolean shouldPause() { return false; }

    @Override public void close() {
        super.close();
        openTime = 0;
    }

}
