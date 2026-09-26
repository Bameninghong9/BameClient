package com.bame.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;
import static com.bame.client.module.LayerMiningPlanner.*;

/** Executes one confirmed movement/break at a time; replans against the live world. */
final class NormalPickaxeMiner {
    private static Bounds bounds;
    private static int top, bottom, ticks, jumpCooldown, idleTicks;
    private static boolean positioned, moving, jumping;
    private static Cell column, breaking;
    private static Step step;
    private static double bestDistance;
    private static final Set<Cell> excluded = new HashSet<>();

    static void reset(MinecraftClient c) {
        release(c);
        if (bounds != null && c.interactionManager != null) c.interactionManager.cancelBlockBreaking();
        bounds = null; step = null; breaking = null; column = null;
        excluded.clear(); positioned = false; ticks = idleTicks = jumpCooldown = 0;
    }

    static void pause(MinecraftClient c) {
        release(c);
        c.interactionManager.cancelBlockBreaking();
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
            public boolean clear(Cell p) { return NormalPickaxeMiner.clear(c,p); }
            public boolean diggable(Cell p) { return NormalPickaxeMiner.diggable(c,p); }
            public boolean support(Cell p) {
                var state = c.world.getBlockState(pos(p));
                return state.getFluidState().isEmpty() && state.isSideSolidFullSquare(c.world,pos(p),Direction.UP);
            }
        };
    }

    static void tick(MinecraftClient c) {
        var a = AutoAreaMinerModule.corner1; var b = AutoAreaMinerModule.corner2;
        Bounds selection = new Bounds(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),
            Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
        if (!selection.equals(bounds)) {
            reset(c); bounds = selection; top = bounds.maxY(); bottom = bottomOfBand(top,bounds.minY());
            long volume = ((long)bounds.maxX()-bounds.minX()+1)*((long)bounds.maxY()-bounds.minY()+1)*((long)bounds.maxZ()-bounds.minZ()+1);
            if (volume > 262144) { stop(c,"Bereich fuer diese Wegsuche zu gross (max. 262144 Bloecke)."); return; }
        }
        if (jumpCooldown > 0) jumpCooldown--;
        // A jump is a pulse, not a permanently held key.
        if (jumping) { c.options.jumpKey.setPressed(false); jumping = false; }
        if (c.currentScreen != null) { pause(c); return; }
        if (step != null) { executeStep(c); return; }
        release(c);
        if (!c.player.isOnGround()) return;
        Cell feet = cell(c.player.getBlockPos());

        if (!positioned || feet.y() != bottom) {
            if (feet.y() == bottom) { positioned = true; excluded.clear(); }
            else {
                var path = route(grid(c), bounds, feet, p -> p.y() == bottom, excluded, Math.min(bottom,feet.y()));
                if (!startPath(c,path)) stop(c,"Keine begehbare Treppe zur naechsten Zweierschicht gefunden.");
                return;
            }
        }

        ArrayList<Cell> remaining = new ArrayList<>();
        for (int x=bounds.minX(); x<=bounds.maxX(); x++) for (int z=bounds.minZ(); z<=bounds.maxZ(); z++)
            for (int y=top; y>=bottom; y--) {
                Cell p = new Cell(x,y,z);
                if (!c.world.getBlockState(pos(p)).isAir() && c.world.getBlockState(pos(p)).getFluidState().isEmpty()) remaining.add(p);
            }
        if (remaining.isEmpty()) {
            if (bottom == bounds.minY()) { stop(c,"Bereich fertig abgebaut."); return; }
            top = bottom - 1; bottom = bottomOfBand(top,bounds.minY());
            positioned = false; column = null; excluded.clear(); return;
        }
        // Finish both blocks of the current column before changing columns.
        remaining.sort(Comparator.comparingDouble(p -> {
            double distance = pos(p).toCenterPos().squaredDistanceTo(c.player.getEyePos());
            return (column != null && p.x()==column.x() && p.z()==column.z() ? -100000 : 0)
                + (p.x()-feet.x())*(double)(p.x()-feet.x())*10 + (p.z()-feet.z())*(double)(p.z()-feet.z())*10
                + (top-p.y())*2 + distance*0.01;
        }));
        if (breaking != null && diggable(c,breaking) && inBand(breaking) && mine(c,breaking)) return;
        for (Cell p : remaining) {
            if (diggable(c,p) && mine(c,p)) { column = p; idleTicks = 0; return; }
        }
        // Travel through an excavated two-high passage toward a remaining column.
        Set<Cell> goals = new HashSet<>();
        for (Cell p : remaining) if (diggable(c,p)) {
            goals.add(new Cell(p.x(),bottom,p.z()));
            for (Direction d : Direction.Type.HORIZONTAL)
                goals.add(new Cell(p.x()+d.getOffsetX(),bottom,p.z()+d.getOffsetZ()));
        }
        var path = route(grid(c),bounds,feet,p -> !p.equals(feet) && goals.contains(p),excluded,bottom);
        if (!startPath(c,path) && ++idleTicks > 20) stop(c,"Schicht blockiert: kein sicherer Abbauweg vorhanden.");
    }

    private static boolean inBand(Cell p) { return p.y()>=bottom && p.y()<=top; }
    private static boolean startPath(MinecraftClient c, List<Step> path) {
        if (path == null || path.isEmpty()) return false;
        step = path.getFirst(); bestDistance = Double.MAX_VALUE; ticks = 0;
        breaking = null; c.interactionManager.cancelBlockBreaking(); return true;
    }
    private static void executeStep(MinecraftClient c) {
        release(c);
        Grid grid = grid(c);
        if (!grid.support(step.to().add(0,-1,0))) { abandonStep(c); return; }
        boolean obstructed = false;
        for (Cell p : step.clearance()) if (!clear(c,p)) {
            obstructed = true;
            // Never remove our current supporting block while preparing a stair.
            if (!p.equals(cell(c.player.getBlockPos()).add(0,-1,0)) && diggable(c,p) && mine(c,p)) return;
        }
        if (obstructed) { abandonStep(c); return; }
        breaking = null; c.interactionManager.cancelBlockBreaking();
        var target = pos(step.to()).toCenterPos();
        double dx=target.x-c.player.getX(), dz=target.z-c.player.getZ();
        double distance=dx*dx+dz*dz;
        if (distance < 0.08 && c.player.isOnGround() && c.player.getBlockY()==step.to().y()) {
            step=null; ticks=0; idleTicks=0; return;
        }
        if (distance < bestDistance-0.005) { bestDistance=distance; ticks=0; }
        else if (++ticks>50) { abandonStep(c); return; }
        // Once centered over a descending waypoint, let gravity finish the step.
        // Continuing forward here would overshoot and turn back and forth in midair.
        if (distance < 0.08) return;
        float yaw=(float)Math.toDegrees(Math.atan2(dz,dx))-90;
        float difference=MathHelper.wrapDegrees(yaw-c.player.getYaw());
        c.player.setYaw(c.player.getYaw()+difference*0.4f);
        c.player.setPitch(c.player.getPitch()*0.6f);
        if (Math.abs(difference)<12) {
            c.options.forwardKey.setPressed(true); moving=true;
            if (step.to().y()>step.from().y() && c.player.isOnGround() && jumpCooldown==0) {
                c.options.jumpKey.setPressed(true); jumping=true; jumpCooldown=15;
            }
        }
    }
    private static void abandonStep(MinecraftClient c) {
        if (step != null) excluded.add(step.to());
        step=null; breaking=null; ticks=0;
        release(c); c.interactionManager.cancelBlockBreaking();
        if (excluded.size()>16) stop(c,"Kein Fortschritt auf den verfuegbaren Wegen.");
    }
    private static BlockHitResult hit(MinecraftClient c, Cell p) {
        Vec3d eye=c.player.getEyePos(), center=pos(p).toCenterPos();
        for (int i=-1;i<6;i++) {
            Vec3d end=center;
            if(i>=0) { Direction d=Direction.values()[i]; end=end.add(d.getOffsetX()*0.49,d.getOffsetY()*0.49,d.getOffsetZ()*0.49); }
            var result=c.world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,c.player));
            if(result.getType()==HitResult.Type.BLOCK && result.getBlockPos().equals(pos(p))
                && eye.distanceTo(result.getPos())<c.player.getBlockInteractionRange()-0.05) return result;
        }
        return null;
    }
    private static boolean mine(MinecraftClient c, Cell p) {
        var visible=hit(c,p); if(visible==null) return false;
        if(!p.equals(breaking)) { c.interactionManager.cancelBlockBreaking(); breaking=p; ticks=0; }
        Vec3d delta=visible.getPos().subtract(c.player.getEyePos());
        double horizontal=Math.hypot(delta.x,delta.z);
        float yaw=horizontal<0.05 ? c.player.getYaw() : (float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90;
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,horizontal));
        c.player.setYaw(c.player.getYaw()+MathHelper.wrapDegrees(yaw-c.player.getYaw())*0.4f);
        c.player.setPitch(c.player.getPitch()+(pitch-c.player.getPitch())*0.4f);
        Vec3d eye=c.player.getEyePos();
        var actual=c.world.raycast(new RaycastContext(eye,eye.add(c.player.getRotationVec(1).multiply(c.player.getBlockInteractionRange())),
            RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,c.player));
        if(actual.getType()==HitResult.Type.BLOCK && actual.getBlockPos().equals(pos(p))) {
            c.interactionManager.updateBlockBreakingProgress(pos(p),actual.getSide()); c.player.swingHand(Hand.MAIN_HAND);
        } else c.interactionManager.cancelBlockBreaking();
        if(++ticks>600) stop(c,"Block konnte seit 30 Sekunden nicht abgebaut werden.");
        return true;
    }
    private static void release(MinecraftClient c) {
        if(c.options!=null) {
            if(moving) c.options.forwardKey.setPressed(false);
            if(jumping) c.options.jumpKey.setPressed(false);
        }
        moving=jumping=false;
    }
    private static void stop(MinecraftClient c,String reason) {
        reset(c); AutoAreaMinerModule.enabled=false; AutoAreaMinerModule.wasEnabled=false;
        com.bame.client.BameClient.LOGGER.info("Miner stopped: {}", reason);
    }
}
