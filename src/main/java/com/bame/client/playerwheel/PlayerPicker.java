package com.bame.client.playerwheel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * Spieler unter dem Mauszeiger bei offenem Chat: Strahl von der Kamera durch die Mausposition
 * ({@link MouseRay} mit der Projektion dieses Bilds aus {@link WorldProjection} und der Kameradrehung), höchstens
 * {@link #MAX_DISTANCE} Blöcke und nie durch Wände (ein Block-Raycast begrenzt die Länge). Nur andere, sichtbare
 * Spieler (nicht man selbst, keine Zuschauer, keine für einen unsichtbaren Spieler). Nur Render-Thread.
 */
public final class PlayerPicker {
    private PlayerPicker() {}

    public static final double MAX_DISTANCE = 48.0;
    /** Etwas großzügiger als die Hitbox, damit auch schmale Spieler in der Ferne gut zu treffen sind. */
    private static final double BOX_MARGIN = 0.1;

    private static final double[] PROJECTION = new double[16];
    private static final double[] INVERSE = new double[16];
    private static final double[] DIR = new double[3];
    private static final double[] TMP = new double[3];

    /**
     * Spieler unter der Mausposition (Fensterpixel wie {@code Mouse.getX/getY}) oder {@code null}.
     */
    public static AbstractClientPlayerEntity pick(MinecraftClient client, double windowX, double windowY) {
        ClientWorld world = client.world;
        ClientPlayerEntity self = client.player;
        if (world == null || self == null) return null;
        Camera camera = client.gameRenderer.getCamera();
        if (!camera.isReady()) return null;
        Window window = client.getWindow();
        double ndcX = MouseRay.ndcX(windowX, window.getWidth());
        double ndcY = MouseRay.ndcY(windowY, window.getHeight());
        if (ndcX < -1.0 || ndcX > 1.0 || ndcY < -1.0 || ndcY > 1.0) return null;
        WorldProjection.get(PROJECTION);
        if (!MouseRay.invert(PROJECTION, INVERSE)) return null;
        if (!MouseRay.worldDirection(INVERSE, camera.getYaw(), camera.getPitch(), ndcX, ndcY, DIR, TMP)) return null;

        Vec3d origin = camera.getCameraPos();
        double maxT = MAX_DISTANCE;
        Vec3d end = origin.add(DIR[0] * maxT, DIR[1] * maxT, DIR[2] * maxT);
        BlockHitResult block = world.raycast(new RaycastContext(origin, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, self));
        if (block != null && block.getType() != HitResult.Type.MISS) {
            maxT = Math.min(maxT, block.getPos().distanceTo(origin));
        }

        float tickDelta = client.getRenderTickCounter().getTickProgress(true);
        AbstractClientPlayerEntity best = null;
        double bestT = maxT;
        List<AbstractClientPlayerEntity> players = world.getPlayers();
        for (int i = 0, n = players.size(); i < n; i++) {
            AbstractClientPlayerEntity p = players.get(i);
            if (!isPickable(p, self)) continue;
            Box box = p.getBoundingBox();
            double ox = MathHelper.lerp(tickDelta, p.lastRenderX, p.getX()) - p.getX();
            double oy = MathHelper.lerp(tickDelta, p.lastRenderY, p.getY()) - p.getY();
            double oz = MathHelper.lerp(tickDelta, p.lastRenderZ, p.getZ()) - p.getZ();
            double m = BOX_MARGIN + p.getTargetingMargin();
            double t = MouseRay.rayBox(origin.x, origin.y, origin.z, DIR[0], DIR[1], DIR[2],
                    box.minX + ox - m, box.minY + oy - m, box.minZ + oz - m,
                    box.maxX + ox + m, box.maxY + oy + m, box.maxZ + oz + m, bestT);
            if (t >= 0.0 && t <= bestT) {
                best = p;
                bestT = t;
            }
        }
        return best;
    }

    /** Anderer, sichtbarer, lebender Spieler in der Welt? */
    public static boolean isPickable(AbstractClientPlayerEntity p, ClientPlayerEntity self) {
        return p != null && p != self && !p.isRemoved() && !p.isSpectator() && !p.isInvisibleTo(self);
    }

    /**
     * Bildschirm-Rechteck (GUI-Pixel) der Hitbox an der gezeichneten Position: {@code out4 = x0, y0, x1, y1}.
     * {@code false}, wenn eine Ecke hinter der Kamera liegt.
     */
    public static boolean screenRect(MinecraftClient client, AbstractClientPlayerEntity p, double[] out4) {
        Camera camera = client.gameRenderer.getCamera();
        if (!camera.isReady()) return false;
        WorldProjection.get(PROJECTION);
        Window window = client.getWindow();
        int fbW = window.getFramebufferWidth();
        int fbH = window.getFramebufferHeight();
        int scale = window.getScaleFactor();
        float tickDelta = client.getRenderTickCounter().getTickProgress(true);
        Vec3d cam = camera.getCameraPos();
        Box box = p.getBoundingBox();
        double ox = MathHelper.lerp(tickDelta, p.lastRenderX, p.getX()) - p.getX() - cam.x;
        double oy = MathHelper.lerp(tickDelta, p.lastRenderY, p.getY()) - p.getY() - cam.y;
        double oz = MathHelper.lerp(tickDelta, p.lastRenderZ, p.getZ()) - p.getZ() - cam.z;
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            double x = ((corner & 1) == 0 ? box.minX : box.maxX) + ox;
            double y = ((corner & 2) == 0 ? box.minY : box.maxY) + oy;
            double z = ((corner & 4) == 0 ? box.minZ : box.maxZ) + oz;
            if (!MouseRay.projectRelative(PROJECTION, yaw, pitch, x, y, z, TMP)) return false;
            double gx = MouseRay.guiFromNdcX(TMP[0], fbW, scale);
            double gy = MouseRay.guiFromNdcY(TMP[1], fbH, scale);
            minX = Math.min(minX, gx);
            minY = Math.min(minY, gy);
            maxX = Math.max(maxX, gx);
            maxY = Math.max(maxY, gy);
        }
        out4[0] = minX;
        out4[1] = minY;
        out4[2] = maxX;
        out4[3] = maxY;
        return true;
    }
}
