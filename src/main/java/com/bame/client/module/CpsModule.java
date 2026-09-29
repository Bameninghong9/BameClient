package com.bame.client.module;

import java.util.ArrayList;
import java.util.List;

public class CpsModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static int hudX = 10;
    public static int hudY = 60;
    public static float scale = 1.0f;
    public static int bgMode = 0; // 0 = Dark, 1 = Transparent, 2 = Blur, 3 = Outline
    public static int outlineColor = 0xFFFFFFFF;
    public static int bgColor = 0xD012161E;
    public static int customWidth = -1;
    public static int customHeight = -1;

    private static final List<Long> leftClicks = new ArrayList<>();
    private static final List<Long> rightClicks = new ArrayList<>();

    public static void registerClick(boolean rightClick) {
        long now = System.currentTimeMillis();
        if (rightClick) {
            rightClicks.add(now);
        } else {
            leftClicks.add(now);
        }
    }

    public static int getLeftCps() {
        long time = System.currentTimeMillis() - 1000;
        leftClicks.removeIf(t -> t < time);
        return leftClicks.size();
    }

    public static int getRightCps() {
        long time = System.currentTimeMillis() - 1000;
        rightClicks.removeIf(t -> t < time);
        return rightClicks.size();
    }
}
