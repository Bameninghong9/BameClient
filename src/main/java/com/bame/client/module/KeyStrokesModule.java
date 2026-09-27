package com.bame.client.module;

import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

public class KeyStrokesModule {
    public static boolean enabled = true;
    public static int keyBind = GLFW.GLFW_KEY_UNKNOWN;
    public static int hudX = 100;
    public static int hudY = 100;
    public static float scale = 1.0f;
    public static int bgMode = 0; // 0 = Dark, 1 = Transparent, 2 = Color, 3 = Chroma
    public static int bgColor = 0xD012161E;

    public static class KeyStroke {
        public String name;
        public int relX, relY;
        public int width, height;
        public int glfwKey;
        public boolean isMouse;

        public KeyStroke(String name, int relX, int relY, int width, int height, int glfwKey, boolean isMouse) {
            this.name = name;
            this.relX = relX;
            this.relY = relY;
            this.width = width;
            this.height = height;
            this.glfwKey = glfwKey;
            this.isMouse = isMouse;
        }

        public KeyBinding getBinding() {
            net.minecraft.client.option.GameOptions options = net.minecraft.client.MinecraftClient.getInstance().options;
            if (glfwKey == GLFW.GLFW_KEY_W) return options.forwardKey;
            if (glfwKey == GLFW.GLFW_KEY_A) return options.leftKey;
            if (glfwKey == GLFW.GLFW_KEY_S) return options.backKey;
            if (glfwKey == GLFW.GLFW_KEY_D) return options.rightKey;
            if (glfwKey == GLFW.GLFW_KEY_SPACE) return options.jumpKey;
            if (isMouse && glfwKey == 0) return options.attackKey;
            if (isMouse && glfwKey == 1) return options.useKey;
            return null;
        }
    }

    public static final List<KeyStroke> keys = new ArrayList<>();

    static {
        resetKeys();
    }

    public static void resetKeys() {
        keys.clear();
        hudX = 100;
        hudY = 100;
        scale = 1.0f;
        bgMode = 0;
        bgColor = 0xD012161E;
        
        int boxSize = 22;
        int gap = 2;

        int row1X = (boxSize + gap);
        keys.add(new KeyStroke("W", row1X, 0, boxSize, boxSize, GLFW.GLFW_KEY_W, false));

        int row2Y = boxSize + gap;
        keys.add(new KeyStroke("A", 0, row2Y, boxSize, boxSize, GLFW.GLFW_KEY_A, false));
        keys.add(new KeyStroke("S", row1X, row2Y, boxSize, boxSize, GLFW.GLFW_KEY_S, false));
        keys.add(new KeyStroke("D", (boxSize + gap) * 2, row2Y, boxSize, boxSize, GLFW.GLFW_KEY_D, false));
    }
}
