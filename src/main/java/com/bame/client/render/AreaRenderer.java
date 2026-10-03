package com.bame.client.render;

import com.bame.client.module.AutoAreaMinerModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

public class AreaRenderer {
    public static void render(WorldRenderContext context) {
        if (AutoAreaMinerModule.corner1 == null || AutoAreaMinerModule.corner2 == null) return;
        
        BlockPos c1 = AutoAreaMinerModule.corner1;
        BlockPos c2 = AutoAreaMinerModule.corner2;
        
        Box box = new Box(
            Math.min(c1.getX(), c2.getX()), Math.min(c1.getY(), c2.getY()), Math.min(c1.getZ(), c2.getZ()),
            Math.max(c1.getX(), c2.getX()) + 1, Math.max(c1.getY(), c2.getY()) + 1, Math.max(c1.getZ(), c2.getZ()) + 1
        );

        Vec3d cameraPos = context.worldState().cameraRenderState.pos;
        
        context.matrices().push();
        context.matrices().translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        int color = com.bame.client.BameClientConfig.outlineColor;
        float r = ((color >>> 16) & 255) / 255f;
        float g = ((color >>> 8) & 255) / 255f;
        float b = (color & 255) / 255f;
        float a = ((color >>> 24) & 255) / 255f;

        int mode = com.bame.client.BameClientConfig.renderMode;

        // 0: Clean
        // 1: Outline
        // 2: Corners
        // 3: Pulse

        if (mode == 0 || mode == 3) {
            VertexConsumer fillBuffer = context.consumers().getBuffer(net.minecraft.client.render.RenderLayers.debugFilledBox());
            org.joml.Matrix4f matrix = context.matrices().peek().getPositionMatrix();
            float fillAlpha = a * 0.2f;
            if (mode == 3) {
                fillAlpha = a * (0.05f + 0.2f * (float)(Math.sin(System.currentTimeMillis() / 250.0) * 0.5 + 0.5));
            }
            fillBox(fillBuffer, matrix, box, r, g, b, fillAlpha);
        }

        if (mode == 0 || mode == 1 || mode == 3) {
            VertexConsumer outlineBuffer = context.consumers().getBuffer(net.minecraft.client.render.RenderLayers.LINES);
            VertexRendering.drawOutline(context.matrices(), outlineBuffer, VoxelShapes.cuboid(box), 0, 0, 0, color, com.bame.client.BameClientConfig.outlineWidth);
        }
        
        if (mode == 2) {
            VertexConsumer buffer = context.consumers().getBuffer(net.minecraft.client.render.RenderLayers.LINES);
            net.minecraft.client.util.math.MatrixStack.Entry entry = context.matrices().peek();
            org.joml.Matrix4f matrix = entry.getPositionMatrix();
            drawCorners(buffer, entry, matrix, box, r, g, b, a);
        }

        context.matrices().pop();
    }
    
    private static void drawCorners(VertexConsumer buffer, net.minecraft.client.util.math.MatrixStack.Entry entry, org.joml.Matrix4f matrix, Box box, float r, float g, float b, float a) {
        float d = 0.35f;
        float minX = (float)box.minX, minY = (float)box.minY, minZ = (float)box.minZ;
        float maxX = (float)box.maxX, maxY = (float)box.maxY, maxZ = (float)box.maxZ;

        drawLine(buffer, entry, matrix, minX, minY, maxZ, minX+d, minY, maxZ, r, g, b, a);
        drawLine(buffer, entry, matrix, minX, minY, maxZ, minX, minY+d, maxZ, r, g, b, a);
        drawLine(buffer, entry, matrix, minX, minY, maxZ, minX, minY, maxZ-d, r, g, b, a);

        drawLine(buffer, entry, matrix, maxX, minY, maxZ, maxX-d, minY, maxZ, r, g, b, a);
        drawLine(buffer, entry, matrix, maxX, minY, maxZ, maxX, minY+d, maxZ, r, g, b, a);
        drawLine(buffer, entry, matrix, maxX, minY, maxZ, maxX, minY, maxZ-d, r, g, b, a);

        drawLine(buffer, entry, matrix, minX, minY, minZ, minX+d, minY, minZ, r, g, b, a);
        drawLine(buffer, entry, matrix, minX, minY, minZ, minX, minY+d, minZ, r, g, b, a);
        drawLine(buffer, entry, matrix, minX, minY, minZ, minX, minY, minZ+d, r, g, b, a);

        drawLine(buffer, entry, matrix, maxX, minY, minZ, maxX-d, minY, minZ, r, g, b, a);
        drawLine(buffer, entry, matrix, maxX, minY, minZ, maxX, minY+d, minZ, r, g, b, a);
        drawLine(buffer, entry, matrix, maxX, minY, minZ, maxX, minY, minZ+d, r, g, b, a);

        drawLine(buffer, entry, matrix, minX, maxY, maxZ, minX+d, maxY, maxZ, r, g, b, a);
        drawLine(buffer, entry, matrix, minX, maxY, maxZ, minX, maxY-d, maxZ, r, g, b, a);
        drawLine(buffer, entry, matrix, minX, maxY, maxZ, minX, maxY, maxZ-d, r, g, b, a);

        drawLine(buffer, entry, matrix, maxX, maxY, maxZ, maxX-d, maxY, maxZ, r, g, b, a);
        drawLine(buffer, entry, matrix, maxX, maxY, maxZ, maxX, maxY-d, maxZ, r, g, b, a);
        drawLine(buffer, entry, matrix, maxX, maxY, maxZ, maxX, maxY, maxZ-d, r, g, b, a);

        drawLine(buffer, entry, matrix, minX, maxY, minZ, minX+d, maxY, minZ, r, g, b, a);
        drawLine(buffer, entry, matrix, minX, maxY, minZ, minX, maxY-d, minZ, r, g, b, a);
        drawLine(buffer, entry, matrix, minX, maxY, minZ, minX, maxY, minZ+d, r, g, b, a);

        drawLine(buffer, entry, matrix, maxX, maxY, minZ, maxX-d, maxY, minZ, r, g, b, a);
        drawLine(buffer, entry, matrix, maxX, maxY, minZ, maxX, maxY-d, minZ, r, g, b, a);
        drawLine(buffer, entry, matrix, maxX, maxY, minZ, maxX, maxY, minZ+d, r, g, b, a);
    }

    private static void fillBox(VertexConsumer buffer, org.joml.Matrix4f matrix, Box box, float r, float g, float b, float a) {
        float minX = (float)box.minX; float minY = (float)box.minY; float minZ = (float)box.minZ;
        float maxX = (float)box.maxX; float maxY = (float)box.maxY; float maxZ = (float)box.maxZ;
        
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
        
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
        
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);
        
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
    }
    
    private static void drawLine(VertexConsumer buffer, net.minecraft.client.util.math.MatrixStack.Entry entry, org.joml.Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length == 0) return;
        nx /= length; ny /= length; nz /= length;
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(entry, nx, ny, nz).lineWidth(com.bame.client.BameClientConfig.outlineWidth);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(entry, nx, ny, nz).lineWidth(com.bame.client.BameClientConfig.outlineWidth);
    }
}
