package com.bame.client.module;

public class CoordinatesModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static int hudX = 10;
    public static int hudY = 106;
    public static float scale = 1.0f;
    public static int bgMode = 0; // 0 = Dark, 1 = Transparent, 2 = Blur, 3 = Outline
    public static int outlineColor = 0xFFFFFFFF;
    public static int bgColor = 0xD012161E;
    public static int customWidth = -1;
    public static int customHeight = -1;

    // Expandable settings in Show HUD
    public static boolean expanded = false;
    public static int layoutMode = 0; // 0 = Horizontal, 1 = Vertical
    public static int style = 0;      // 0 = Labeled, 1 = Numbers, 2 = Prefix, 3 = Brackets
    public static boolean showX = true;
    public static boolean showY = true;
    public static boolean showZ = true;
    public static boolean decimals = false;
    public static boolean showNether = false;
    public static boolean showDirection = false;

    public static final String[] STYLES = {"Labeled", "Numbers", "Prefix", "Brackets"};

    public static String getStyleName() {
        if (style < 0 || style >= STYLES.length) style = 0;
        return STYLES[style];
    }

    public static void cycleStyle() {
        style = (style + 1) % STYLES.length;
    }

    public static String getLayoutName() {
        return layoutMode == 1 ? "Vertical" : "Horizontal";
    }

    public static void cycleLayout() {
        layoutMode = (layoutMode == 0) ? 1 : 0;
    }
}
