package com.bame.client.render;
import net.minecraft.client.render.VertexConsumer;
public class Test {
    public static void test(VertexConsumer v) {
        v.vertex(0, 0, 0).color(1f, 1f, 1f, 1f).normal(0, 0, 0).light(0).overlay(0).lineWidth(1.0f);
    }
}


