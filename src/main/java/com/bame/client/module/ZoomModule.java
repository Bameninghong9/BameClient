package com.bame.client.module;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MinecraftClient;

public class ZoomModule {
    public static boolean enabled = false;
    public static int keyBind = GLFW.GLFW_KEY_C;
    public static int mode = 0; // 0 = Smooth, 1 = Instant
    public static boolean expanded = false;
    
    // Smooth zoom state
    public static double currentZoom = 1.0;
    
    public static boolean isZooming() {
        MinecraftClient client = MinecraftClient.getInstance();
        return enabled && keyBind != GLFW.GLFW_KEY_UNKNOWN && client.currentScreen == null && org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), keyBind) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
}
