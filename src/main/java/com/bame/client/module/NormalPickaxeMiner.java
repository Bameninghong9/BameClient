package com.bame.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;
import static com.bame.client.module.LayerMiningPlanner.*;

/** Executes top-down mining of the marked bounds; mines reachable blocks first and pathfinds when needed. */
final class NormalPickaxeMiner {
    private static Bounds bounds;
    private static int top, bottom, ticks, jumpCooldown, idleTicks;
    private static boolean moving, jumping;
    private static Cell column, breaking;
    private static Step step;
    private static double bestDistance;
    private static final Set<Cell> excluded = new HashSet<>();

    static void reset(MinecraftClient c) {
        release(c);
        if (bounds != null && c.interactionManager != null) c.interactionManager.cancelBlockBreaking();
        bounds = null; step = null; breaking = null; column = null;
        excluded.clear(); ticks = idleTicks = jumpCooldown = 0;
    }

    static void pause(MinecraftClient c) {
        release(c);
        if (c.interactionManager != null) c.interactionManager.cancelBlockBreaking();
        step = null; breaking = null; ticks = 0;
    }

    private static BlockPos pos(Cell p) { return new BlockPos(p.x(), p.y(), p.z()); }
    private static Cell cell(BlockPos p) { return new Cell(p.getX(), p.getY(), p.getZ()); }

    private static boolean clear(MinecraftClient c, Cell p) {
        var state = c.world.getBlockState(pos(p));
        return state.getFluidState().isEmpty() && state.getCollisionShape(c.world, pos(p)).isEmpty();
    }

    private static boolean diggable(MinecraftClient c, Cell p) {
        var state = c.world.getBlockState(pos(p));
        return bounds.contains(p) && !state.isAir() && state.getFluidState().isEmpty()
            && state.getHardness(c.world, pos(p)) >= 0;
    }

    private static Grid grid(MinecraftClient c) {
        return new Grid() {
            public boolean clear(Cell p) { return NormalPickaxeMiner.clear(c, p); }
            public boolean diggable(Cell p) { return NormalPickaxeMiner.diggable(c, p); }
            public boolean support(Cell p) {
                var state = c.world.getBlockState(pos(p));
                return state.getFluidState().isEmpty() && state.isSideSolidFullSquare(c.world, pos(p), Direction.UP);
            }
        };
    }

    static void tick(MinecraftClient c) {
        var a = AutoAreaMinerModule.corner1;
        var b = AutoAreaMinerModule.corner2;
        if (a == null || b == null) {
            stop(c, "Corner 1 oder Corner 2 fehlt.");
            return;
        }

        Bounds selection = new Bounds(
            Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
            Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())
        );

        if (!selection.equals(bounds)) {
            reset(c);
            bounds = selection;
            top = bounds.maxY();
            bottom = bottomOfBand(top, bounds.minY());
            long volume = ((long) bounds.maxX() - bounds.minX() + 1) * ((long) bounds.maxY() - bounds.minY() + 1) * ((long) bounds.maxZ() - bounds.minZ() + 1);
            if (volume > 262144) {
                stop(c, "Bereich fuer Wegsuche zu gross (max. 262144 Bloecke).");
                return;
            }
        }

        if (jumpCooldown > 0) jumpCooldown--;
        if (jumping) {
            c.options.jumpKey.setPressed(false);
            jumping = false;
        }
        if (c.currentScreen != null) {
            pause(c);
            return;
        }
        if (step != null) {
            executeStep(c);
            return;
        }
        release(c);

        if (!c.player.isOnGround()) return;
        Cell feet = cell(c.player.getBlockPos());

        // 1. If we are already breaking a block in this band and it's still valid, continue mining it directly!
        if (breaking != null) {
            if (diggable(c, breaking) && inBand(breaking)) {
                if (mine(c, breaking)) {
                    return;
                }
            } else {
                breaking = null;
                if (c.interactionManager != null) c.interactionManager.cancelBlockBreaking();
            }
        }

        // 2. Collect all remaining blocks in current band (from top to bottom)
        ArrayList<Cell> remaining = new ArrayList<>();
        for (int y = top; y >= bottom; y--) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    Cell p = new Cell(x, y, z);
                    if (diggable(c, p)) remaining.add(p);
                }
            }
        }

        // If the current band has no remaining blocks:
        if (remaining.isEmpty()) {
            if (bottom == bounds.minY()) {
                stop(c, "§aBereich fertig abgebaut!");
                return;
            }
            top = bottom - 1;
            bottom = bottomOfBand(top, bounds.minY());
            column = null;
            excluded.clear();
            idleTicks = 0;
            return;
        }

        // 3. Sort candidates to prioritize in-reach, current column, higher Y, and closest (zero raycasts inside comparator!)
        double reach = c.player.getBlockInteractionRange() + 0.5;
        double maxReachSq = reach * reach;
        Vec3d eye = c.player.getEyePos();

        remaining.sort((p1, p2) -> {
            // Finish column before changing columns
            boolean col1 = (column != null && p1.x() == column.x() && p1.z() == column.z());
            boolean col2 = (column != null && p2.x() == column.x() && p2.z() == column.z());
            if (col1 != col2) return col1 ? -1 : 1;
            // Avoid breaking supporting block right under feet until necessary
            boolean underFeet1 = p1.equals(feet.add(0, -1, 0));
            boolean underFeet2 = p2.equals(feet.add(0, -1, 0));
            if (underFeet1 != underFeet2) return underFeet1 ? 1 : -1;
            double d1 = pos(p1).toCenterPos().squaredDistanceTo(eye);
            double d2 = pos(p2).toCenterPos().squaredDistanceTo(eye);
            boolean inReach1 = d1 <= maxReachSq;
            boolean inReach2 = d2 <= maxReachSq;
            if (inReach1 != inReach2) return inReach1 ? -1 : 1;
            if (p1.y() != p2.y()) return Integer.compare(p2.y(), p1.y()); // higher Y first
            return Double.compare(d1, d2);
        });

        // 4. Try to directly mine any reachable block
        for (Cell p : remaining) {
            double d = pos(p).toCenterPos().squaredDistanceTo(eye);
            if (d <= maxReachSq && diggable(c, p)) {
                if (mine(c, p)) {
                    column = p;
                    idleTicks = 0;
                    return;
                }
            }
        }

        // 5. If no block is directly reachable, pathfind towards remaining blocks
        Set<Cell> goals = new HashSet<>();
        for (Cell p : remaining) {
            if (diggable(c, p)) {
                goals.add(new Cell(p.x(), bottom, p.z()));
                for (Direction d : Direction.Type.HORIZONTAL)
                    goals.add(new Cell(p.x() + d.getOffsetX(), bottom, p.z() + d.getOffsetZ()));
            }
        }

        var path = route(grid(c), bounds, feet, p -> !p.equals(feet) && goals.contains(p), excluded, Math.min(bottom, feet.y()));
        if (path == null && feet.y() != bottom) {
            path = route(grid(c), bounds, feet, p -> p.y() == bottom, excluded, Math.min(bottom, feet.y()));
        }
        if (startPath(c, path)) {
            idleTicks = 0;
            return;
        }

        if (++idleTicks > 60) {
            stop(c, "Kein begehbarer Weg zu den verbleibenden Blöcken gefunden.");
        }
    }

    private static boolean inBand(Cell p) { return p.y() >= bottom && p.y() <= top; }

    private static boolean startPath(MinecraftClient c, List<Step> path) {
        if (path == null || path.isEmpty()) return false;
        step = path.getFirst();
        bestDistance = Double.MAX_VALUE;
        ticks = 0;
        breaking = null;
        if (c.interactionManager != null) c.interactionManager.cancelBlockBreaking();
        return true;
    }

    private static void executeStep(MinecraftClient c) {
        release(c);
        Grid grid = grid(c);
        if (!grid.support(step.to().add(0, -1, 0))) {
            abandonStep(c);
            return;
        }
        boolean obstructed = false;
        for (Cell p : step.clearance()) {
            if (!clear(c, p)) {
                obstructed = true;
                if (!p.equals(cell(c.player.getBlockPos()).add(0, -1, 0)) && diggable(c, p) && mine(c, p)) return;
            }
        }
        if (obstructed) {
            abandonStep(c);
            return;
        }
        breaking = null;
        if (c.interactionManager != null) c.interactionManager.cancelBlockBreaking();
        var target = pos(step.to()).toCenterPos();
        double dx = target.x - c.player.getX(), dz = target.z - c.player.getZ();
        double distance = dx * dx + dz * dz;
        if (distance < 0.08 && c.player.isOnGround() && c.player.getBlockY() == step.to().y()) {
            step = null;
            ticks = 0;
            idleTicks = 0;
            return;
        }
        if (distance < bestDistance - 0.005) {
            bestDistance = distance;
            ticks = 0;
        } else if (++ticks > 50) {
            abandonStep(c);
            return;
        }
        if (distance < 0.08) return;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float difference = MathHelper.wrapDegrees(yaw - c.player.getYaw());
        c.player.setYaw(c.player.getYaw() + difference * 0.4f);
        c.player.setPitch(c.player.getPitch() * 0.6f);
        if (Math.abs(difference) < 12) {
            c.options.forwardKey.setPressed(true);
            moving = true;
            if (step.to().y() > step.from().y() && c.player.isOnGround() && jumpCooldown == 0) {
                c.options.jumpKey.setPressed(true);
                jumping = true;
                jumpCooldown = 15;
            }
        }
    }

    private static void abandonStep(MinecraftClient c) {
        if (step != null) excluded.add(step.to());
        step = null;
        breaking = null;
        ticks = 0;
        release(c);
        if (c.interactionManager != null) c.interactionManager.cancelBlockBreaking();
        if (excluded.size() > 64) stop(c, "Kein Fortschritt auf den verfuegbaren Wegen.");
    }

    private static BlockHitResult hit(MinecraftClient c, Cell p) {
        Vec3d eye = c.player.getEyePos(), center = pos(p).toCenterPos();
        for (int i = -1; i < 6; i++) {
            Vec3d end = center;
            if (i >= 0) {
                Direction d = Direction.values()[i];
                end = end.add(d.getOffsetX() * 0.49, d.getOffsetY() * 0.49, d.getOffsetZ() * 0.49);
            }
            var result = c.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, c.player));
            if (result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(pos(p))
                && eye.distanceTo(result.getPos()) < c.player.getBlockInteractionRange() - 0.05) {
                return result;
            }
        }
        return null;
    }

    private static boolean mine(MinecraftClient c, Cell p) {
        var visible = hit(c, p);
        if (visible == null) return false;
        if (!p.equals(breaking)) {
            if (c.interactionManager != null) c.interactionManager.cancelBlockBreaking();
            breaking = p;
            ticks = 0;
        }
        Vec3d delta = visible.getPos().subtract(c.player.getEyePos());
        double horizontal = Math.hypot(delta.x, delta.z);
        float yaw = horizontal < 0.05 ? c.player.getYaw() : (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        c.player.setYaw(yaw);
        c.player.setPitch(pitch);
        c.interactionManager.updateBlockBreakingProgress(pos(p), visible.getSide());
        c.player.swingHand(Hand.MAIN_HAND);
        if (++ticks > 600) stop(c, "Block konnte seit 30 Sekunden nicht abgebaut werden.");
        return true;
    }

    private static void release(MinecraftClient c) {
        if (c.options != null) {
            if (moving) c.options.forwardKey.setPressed(false);
            if (jumping) c.options.jumpKey.setPressed(false);
        }
        moving = jumping = false;
    }

    private static void stop(MinecraftClient c, String reason) {
        reset(c);
        AutoAreaMinerModule.enabled = false;
        AutoAreaMinerModule.wasEnabled = false;
        com.bame.client.BameClient.LOGGER.info("Miner stopped: {}", reason);
        if (c.player != null) {
            c.player.sendMessage(Text.literal("§c[AutoAreaMiner] " + reason), false);
        }
    }
}
