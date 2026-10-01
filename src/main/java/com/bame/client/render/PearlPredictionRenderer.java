package com.bame.client.render;

import com.bame.client.module.PearlPredictionModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

public class PearlPredictionRenderer {

    public static void render(WorldRenderContext context) {
        if (!PearlPredictionModule.enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) return;

        Vec3d cameraPos = context.worldState().cameraRenderState.pos;

        // 1. In-flight Ender Pearls in the world
        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof EnderPearlEntity pearl && pearl.isAlive()) {
                boolean isOwnPearl = (pearl.getOwner() == client.player);
                if (PearlPredictionModule.enemyOnly && isOwnPearl) {
                    continue;
                }

                // Orange for enemy pearl (matching media_1790874369333.png), Cyan for own pearl
                int color = isOwnPearl ? 0xFF00EEEE : 0xFFFF7700;
                simulateAndRender(context, cameraPos, pearl.getEntityPos(), pearl.getVelocity(), pearl, color);
            }
        }

        // 2. Player holding an Ender Pearl in mainhand or offhand (Throw Preview)
        if (!PearlPredictionModule.enemyOnly && (PearlPredictionModule.throwPreview || PearlPredictionModule.landingBox)) {
            ItemStack main = client.player.getMainHandStack();
            ItemStack off = client.player.getOffHandStack();
            if ((main != null && main.isOf(Items.ENDER_PEARL)) || (off != null && off.isOf(Items.ENDER_PEARL))) {
                Vec3d launchPos = new Vec3d(client.player.getX(), client.player.getEyeY() - 0.1, client.player.getZ());
                float pitch = client.player.getPitch();
                float yaw = client.player.getYaw();
                float f = 0.017453292F;
                float x = -MathHelper.sin(yaw * f) * MathHelper.cos(pitch * f);
                float y = -MathHelper.sin(pitch * f);
                float z = MathHelper.cos(yaw * f) * MathHelper.cos(pitch * f);
                Vec3d dir = new Vec3d(x, y, z).normalize();
                Vec3d launchVel = dir.multiply(1.5);
                Vec3d pVel = client.player.getVelocity();
                launchVel = launchVel.add(pVel.x, client.player.isOnGround() ? 0.0 : pVel.y, pVel.z);

                // Cyan trajectory line & landing box (matching media_1790874294861.png)
                int previewColor = 0xFF00EEEE;
                simulateAndRender(context, cameraPos, launchPos, launchVel, client.player, previewColor);
            }
        }
    }

    private static void simulateAndRender(WorldRenderContext context, Vec3d cameraPos, Vec3d startPos, Vec3d startVel, Entity ignoreEntity, int color) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) return;

        List<Vec3d> points = new ArrayList<>();
        points.add(startPos);

        Vec3d pos = startPos;
        Vec3d vel = startVel;
        Vec3d hitPos = null;

        for (int step = 0; step < 160; step++) {
            Vec3d nextPos = pos.add(vel);

            RaycastContext raycastContext = new RaycastContext(
                    pos, nextPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    ignoreEntity != null ? ignoreEntity : client.player
            );
            BlockHitResult blockHit = world.raycast(raycastContext);
            Vec3d stepEnd = (blockHit != null && blockHit.getType() != HitResult.Type.MISS) ? blockHit.getPos() : nextPos;

            Box stepBox = new Box(pos, stepEnd).expand(0.8);
            EntityHitResult entityHit = ProjectileUtil.getEntityCollision(
                    world,
                    ignoreEntity != null ? ignoreEntity : client.player,
                    pos, stepEnd, stepBox,
                    e -> !e.isSpectator() && e.canHit() && e != (ignoreEntity != null ? (ignoreEntity instanceof EnderPearlEntity ep ? ep.getOwner() : ignoreEntity) : client.player),
                    0.0f
            );

            if (entityHit != null) {
                hitPos = entityHit.getPos();
                points.add(hitPos);
                break;
            } else if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
                hitPos = blockHit.getPos();
                points.add(hitPos);
                break;
            }

            points.add(nextPos);
            pos = nextPos;

            // Gravity: 0.03, Drag: 0.99
            vel = vel.multiply(0.99).subtract(0, 0.03, 0);
        }

        context.matrices().push();
        context.matrices().translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        float r = ((color >>> 16) & 255) / 255f;
        float g = ((color >>> 8) & 255) / 255f;
        float b = (color & 255) / 255f;
        float a = 0.95f;

        // 1. Draw Trajectory Line (Throw Preview)
        if (PearlPredictionModule.throwPreview && points.size() > 1) {
            VertexConsumer lineBuffer = context.consumers().getBuffer(net.minecraft.client.render.RenderLayers.LINES);
            net.minecraft.client.util.math.MatrixStack.Entry entry = context.matrices().peek();
            org.joml.Matrix4f matrix = entry.getPositionMatrix();

            for (int i = 0; i < points.size() - 1; i++) {
                Vec3d p1 = points.get(i);
                Vec3d p2 = points.get(i + 1);
                drawLine(lineBuffer, entry, matrix, (float) p1.x, (float) p1.y, (float) p1.z, (float) p2.x, (float) p2.y, (float) p2.z, r, g, b, a);
            }
        }

        // 2. Draw Landing Box
        if (PearlPredictionModule.landingBox && hitPos != null) {
            Box box = new Box(hitPos.x - 0.35, hitPos.y, hitPos.z - 0.35, hitPos.x + 0.35, hitPos.y + 0.6, hitPos.z + 0.35);

            // Subtle translucent fill
            VertexConsumer fillBuffer = context.consumers().getBuffer(net.minecraft.client.render.RenderLayers.debugFilledBox());
            org.joml.Matrix4f matrix = context.matrices().peek().getPositionMatrix();
            fillBox(fillBuffer, matrix, box, r, g, b, 0.25f);

            // Clean 3D outline box
            VertexConsumer outlineBuffer = context.consumers().getBuffer(net.minecraft.client.render.RenderLayers.LINES);
            VertexRendering.drawOutline(context.matrices(), outlineBuffer, VoxelShapes.cuboid(box), 0, 0, 0, color, 2.0f);
        }

        context.matrices().pop();
    }

    private static void drawLine(VertexConsumer buffer, net.minecraft.client.util.math.MatrixStack.Entry entry, org.joml.Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length == 0) return;
        nx /= length; ny /= length; nz /= length;
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(entry, nx, ny, nz).lineWidth(2.0f);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(entry, nx, ny, nz).lineWidth(2.0f);
    }

    private static void fillBox(VertexConsumer buffer, org.joml.Matrix4f matrix, Box box, float r, float g, float b, float a) {
        float minX = (float) box.minX; float minY = (float) box.minY; float minZ = (float) box.minZ;
        float maxX = (float) box.maxX; float maxY = (float) box.maxY; float maxZ = (float) box.maxZ;

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
}
