package com.bame.client.render;

import com.bame.client.module.AutoAreaMinerModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.render.RenderLayers;
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
        
        VertexConsumer buffer = context.consumers().getBuffer(RenderLayers.LINES);
        
        // 0xFF00FFFF (ARGB Cyan)
        VertexRendering.drawOutline(context.matrices(), buffer, VoxelShapes.cuboid(box), 0, 0, 0, com.bame.client.BameClientConfig.outlineColor, 1.0f);
        
        // Draw grid lines to make it look like a transparent wall
        int color = com.bame.client.BameClientConfig.outlineColor;
        float r = ((color >>> 16) & 255) / 255f;
        float g = ((color >>> 8) & 255) / 255f;
        float b = (color & 255) / 255f;
        float a = ((color >>> 24) & 255) / 255f * 0.15f;
        net.minecraft.client.util.math.MatrixStack.Entry entry = context.matrices().peek();
        org.joml.Matrix4f matrix = entry.getPositionMatrix();
        
        // Front and back faces
        for (double x = box.minX + 0.25; x < box.maxX; x += 0.25) {
            drawLine(buffer, entry, matrix, (float)x, (float)box.minY, (float)box.minZ, (float)x, (float)box.maxY, (float)box.minZ, r, g, b, a);
            drawLine(buffer, entry, matrix, (float)x, (float)box.minY, (float)box.maxZ, (float)x, (float)box.maxY, (float)box.maxZ, r, g, b, a);
        }
        for (double y = box.minY + 0.25; y < box.maxY; y += 0.25) {
            drawLine(buffer, entry, matrix, (float)box.minX, (float)y, (float)box.minZ, (float)box.maxX, (float)y, (float)box.minZ, r, g, b, a);
            drawLine(buffer, entry, matrix, (float)box.minX, (float)y, (float)box.maxZ, (float)box.maxX, (float)y, (float)box.maxZ, r, g, b, a);
        }
        
        // Left and right faces
        for (double z = box.minZ + 0.25; z < box.maxZ; z += 0.25) {
            drawLine(buffer, entry, matrix, (float)box.minX, (float)box.minY, (float)z, (float)box.minX, (float)box.maxY, (float)z, r, g, b, a);
            drawLine(buffer, entry, matrix, (float)box.maxX, (float)box.minY, (float)z, (float)box.maxX, (float)box.maxY, (float)z, r, g, b, a);
            
            // Top and bottom faces
            drawLine(buffer, entry, matrix, (float)box.minX, (float)box.minY, (float)z, (float)box.maxX, (float)box.minY, (float)z, r, g, b, a);
            drawLine(buffer, entry, matrix, (float)box.minX, (float)box.maxY, (float)z, (float)box.maxX, (float)box.maxY, (float)z, r, g, b, a);
        }
        
        context.matrices().pop();
    }
    
    private static void drawLine(VertexConsumer buffer, net.minecraft.client.util.math.MatrixStack.Entry entry, org.joml.Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(entry, nx, ny, nz).lineWidth(1.0f);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(entry, nx, ny, nz).lineWidth(1.0f);
    }
}
