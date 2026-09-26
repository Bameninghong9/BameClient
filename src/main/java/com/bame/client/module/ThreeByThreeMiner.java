package com.bame.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;
import static com.bame.client.module.LayerMiningPlanner.*;
import static com.bame.client.module.ThreeByThreePattern.Axis;

/** Fixed serpentine execution; no nearest-block target selection. */
final class ThreeByThreeMiner {
    private enum Phase { ALIGN_TOP, DESCEND, ALIGN_SIDE, SIDE_ENTRY, SWEEP, VERIFY, CLEANUP }
    private static Bounds bounds;
    private static ThreeByThreeLanes.Plan plan;
    private static Phase phase;
    private static int top,bottom,index,breakTicks,blockedTicks,settleTicks,moveTicks;
    private static boolean alongX,moving;
    private static Cell breaking,moveGoal;
    private static Cell cleanupTarget;
    private static List<Cell> recoveryPanel;
    private static Phase resumePhase;
    private static Cell resumePosition;
    private static boolean returningToLane;
    private static int emptyChecks;
    private static final Set<Cell> skipped = new HashSet<>();
    private static double bestDistance;
    private static BlockPos pos(Cell p) { return new BlockPos(p.x(),p.y(),p.z()); }
    private static Cell feet(MinecraftClient c) { var p=c.player.getBlockPos(); return new Cell(p.getX(),p.getY(),p.getZ()); }
    static void reset(MinecraftClient c) {
        release(c);
        if(bounds!=null && c.interactionManager!=null) c.interactionManager.cancelBlockBreaking();
        bounds=null; plan=null; phase=null; breaking=moveGoal=null;
        cleanupTarget=null; emptyChecks=0; skipped.clear();
        recoveryPanel=null; resumePhase=null; resumePosition=null; returningToLane=false;
        index=breakTicks=blockedTicks=settleTicks=moveTicks=0;
    }
    private static void release(MinecraftClient c) {
        if(moving && c.options!=null) c.options.forwardKey.setPressed(false);
        moving=false;
    }
    private static void stop(MinecraftClient c,String reason) {
        reset(c); AutoAreaMinerModule.enabled=false; AutoAreaMinerModule.wasEnabled=false;
        com.bame.client.BameClient.LOGGER.info("Miner stopped: {}", reason);
    }
    private static boolean clear(MinecraftClient c,Cell p) {
        var s=c.world.getBlockState(pos(p));
        return s.getFluidState().isEmpty() && s.getCollisionShape(c.world,pos(p)).isEmpty();
    }
    private static boolean support(MinecraftClient c,Cell p) {
        var s=c.world.getBlockState(pos(p));
        return s.getFluidState().isEmpty() && s.isSideSolidFullSquare(c.world,pos(p),Direction.UP);
    }
    private static Grid walkingGrid(MinecraftClient c) {
        return new Grid() {
            public boolean clear(Cell p) { return ThreeByThreeMiner.clear(c,p); }
            public boolean support(Cell p) { return ThreeByThreeMiner.support(c,p); }
            public boolean diggable(Cell p) { return false; }
        };
    }
    private static void beginBand(MinecraftClient c,boolean fromAbove) {
        bottom=ThreeByThreePattern.bottom(top,bounds.minY());
        var band=new Bounds(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),top,bounds.maxZ());
        plan=ThreeByThreeLanes.create(band,bottom,alongX,feet(c));
        // A skipped pillar may occupy the usual next-layer entry. Try the other corners.
        if(fromAbove && !skipped.isEmpty()) {
            Cell start=feet(c);
            var options=new ArrayList<ThreeByThreeLanes.Plan>();
            options.add(plan);
            for(int x:new int[]{bounds.minX(),bounds.maxX()}) for(int z:new int[]{bounds.minZ(),bounds.maxZ()})
                options.add(ThreeByThreeLanes.create(band,bottom,alongX,new Cell(x,top+1,z)));
            for(var candidate:options) {
                Cell goal=new Cell(candidate.entry().x(),top+1,candidate.entry().z());
                if(!clear(c,goal) || !clear(c,goal.add(0,1,0)) || !support(c,goal.add(0,-1,0))) continue;
                var path=LayerMiningPlanner.route(walkingGrid(c),bounds,start,p->p.equals(goal),Set.of(),start.y());
                if(start.equals(goal) || (path!=null && path.stream().allMatch(step->step.to().y()==start.y()))) {
                    plan=candidate; break;
                }
            }
        }
        phase=fromAbove?Phase.ALIGN_TOP:Phase.ALIGN_SIDE;
        index=blockedTicks=0; moveGoal=breaking=null;
    }
    static void tick(MinecraftClient c) {
        var a=AutoAreaMinerModule.corner1; var b=AutoAreaMinerModule.corner2;
        var selection=new Bounds(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),
            Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
        if(!selection.equals(bounds)) {
            reset(c); bounds=selection; top=bounds.maxY();
            long volume=((long)bounds.maxX()-bounds.minX()+1)*((long)bounds.maxY()-bounds.minY()+1)*((long)bounds.maxZ()-bounds.minZ()+1);
            if(volume>262144) { stop(c,"Markierung zu gross (max. 262144 Bloecke)."); return; }
            boolean above=c.player.getY()>=top+0.9;
            if(top-bounds.minY()+1>3 && (!above || !bounds.contains(new Cell(feet(c).x(),top,feet(c).z())))) {
                stop(c,"Bei hohen Bereichen vor dem Einschalten oben auf die Markierung stellen."); return;
            }
            alongX=bounds.maxX()-bounds.minX()>=bounds.maxZ()-bounds.minZ();
            beginBand(c,above);
        }
        if(c.currentScreen!=null) {
            release(c); c.interactionManager.cancelBlockBreaking(); breaking=moveGoal=null; breakTicks=moveTicks=0; return;
        }
        if(settleTicks>0) { release(c); settleTicks--; return; }
        if(breaking!=null && c.world.getBlockState(pos(breaking)).isAir()) {
            release(c); c.interactionManager.cancelBlockBreaking(); breaking=null; breakTicks=0; settleTicks=4; return;
        }
        release(c);
        if(!c.player.isOnGround()) return;
        switch(phase) {
            case ALIGN_TOP -> {
                var goal=new Cell(plan.entry().x(),top+1,plan.entry().z());
                if(align(c,goal)) { phase=Phase.DESCEND; blockedTicks=0; }
            }
            case DESCEND -> {
                if(feet(c).y()==bottom) { phase=Phase.SWEEP; index=0; break; }
                if(feet(c).y()<bottom) { stop(c,"Unter die geplante Schicht gefallen."); break; }
                if(!support(c,new Cell(plan.entry().x(),bottom-1,plan.entry().z()))) {
                    stop(c,"Kein fester Boden unter dem geplanten Einstieg."); break;
                }
                panel(c,new Cell(plan.entry().x(),feet(c).y()-1,plan.entry().z()),Axis.Y,true);
            }
            case ALIGN_SIDE -> {
                if(align(c,plan.outside())) { phase=Phase.SIDE_ENTRY; index=0; blockedTicks=0; }
            }
            case SIDE_ENTRY, SWEEP -> {
                var actions=phase==Phase.SIDE_ENTRY?plan.sideEntry():plan.sweep();
                if(index>=actions.size()) { phase=phase==Phase.SIDE_ENTRY?Phase.SWEEP:Phase.VERIFY; index=0; break; }
                var action=actions.get(index);
                boolean done=action.walk()?walk(c,action.center()):panel(c,action.center(),action.normal(),false);
                if(done && bounds!=null) { index++; blockedTicks=0; }
            }
            case VERIFY -> {
                // Recheck all processed layers, including delayed server updates and corner pillars.
                var remnants=remnants(c);
                if(!remnants.isEmpty()) {
                    phase=Phase.CLEANUP; cleanupTarget=remnants.getFirst(); emptyChecks=0; moveGoal=null; return;
                }
                if(++emptyChecks<2) { settleTicks=10; return; }
                emptyChecks=0;
                if(bottom==bounds.minY()) { long remaining=skipped.stream().filter(p->breakable(c,p)).count();
                    stop(c,remaining==0 ? "Bereich fertig abgebaut." : "Durchgang beendet; "+remaining+" Restbloecke uebersprungen."); return; }
                top=bottom-1; beginBand(c,true);
            }
            case CLEANUP -> cleanup(c);
        }
    }
    private static List<Cell> remnants(MinecraftClient c) {
        return ThreeByThreePattern.remnants(bounds,bottom,p->!skipped.contains(p) && breakable(c,p));
    }
    private static void cleanup(MinecraftClient c) {
        var left=recoveryPanel==null ? remnants(c) : recoveryPanel.stream().filter(p->!skipped.contains(p) && breakable(c,p)).toList();
        if(left.isEmpty()) {
            if(resumePhase!=null) {
                if(!returningToLane) { moveGoal=null; returningToLane=true; }
                if(!align(c,resumePosition)) return;
                phase=resumePhase; resumePhase=null; resumePosition=null; recoveryPanel=null;
                returningToLane=false; blockedTicks=0;
            } else phase=Phase.VERIFY;
            cleanupTarget=null; moveGoal=null; emptyChecks=0; return;
        }
        // Try every remaining block, not just the highest block which may be hidden.
        var candidates=new ArrayList<>(left);
        if(cleanupTarget!=null && candidates.remove(cleanupTarget)) candidates.addFirst(cleanupTarget);
        for(Cell candidate:candidates) for(Axis axis:Axis.values()) if(allowed(c,candidate,axis,false)) {
            var visible=hit(c,candidate,axis,false);
            if(visible!=null) { cleanupTarget=candidate; moveGoal=null; mine(c,candidate,visible,axis,false); return; }
        }
        if(moveGoal!=null) { walk(c,moveGoal); return; }
        Cell start=feet(c);
        Grid levelWalking=new Grid() {
            public boolean clear(Cell p) { return ThreeByThreeMiner.clear(c,p); }
            public boolean diggable(Cell p) { return false; }
            public boolean support(Cell p) { return p.y()==start.y()-1 && ThreeByThreeMiner.support(c,p); }
        };
        for(Cell candidate:candidates) {
            cleanupTarget=candidate;
            // A small centering correction can reveal a face without changing cells.
            if(!at(c,start) && cleanupVisibleFrom(c,start)) { walk(c,start); return; }
            var route=LayerMiningPlanner.route(levelWalking,bounds,start,
                p->!p.equals(start) && cleanupVisibleFrom(c,p),Set.of(),start.y());
            if(route!=null && !route.isEmpty()) { walk(c,route.getFirst().to()); return; }
        }
        com.bame.client.BameClient.LOGGER.warn("3x3 recovery blocked: phase={}, laneIndex={}, player={}, remnants={}",
            resumePhase,index,start,candidates);
        // Failed candidates must not keep pulling VERIFY back into this recovery forever.
        skipped.addAll(candidates);
        release(c); c.interactionManager.cancelBlockBreaking();
        recoveryPanel=null; resumePhase=null; resumePosition=null; returningToLane=false;
        cleanupTarget=moveGoal=breaking=null;
        blockedTicks=breakTicks=moveTicks=emptyChecks=0;
        phase=Phase.VERIFY;
        com.bame.client.BameClient.LOGGER.info("3x3: skipped {} unreachable blocks", candidates.size());
    }
    private static boolean cleanupVisibleFrom(MinecraftClient c,Cell stand) {
        if(!clear(c,stand) || !clear(c,stand.add(0,1,0)) || !support(c,stand.add(0,-1,0))) return false;
        Vec3d eye=new Vec3d(stand.x()+0.5,stand.y()+c.player.getEyeY()-c.player.getY(),stand.z()+0.5);
        Vec3d center=pos(cleanupTarget).toCenterPos();
        for(Direction d:Direction.values()) {
            Axis axis=Axis.valueOf(d.getAxis().name());
            if(!allowed(c,cleanupTarget,axis,false)) continue;
            // A different stand must not become part of the affected plane either.
            if(ThreeByThreePattern.footprint(cleanupTarget,axis).contains(stand.add(0,-1,0))) continue;
            Vec3d end=center.add(d.getOffsetX()*0.499,d.getOffsetY()*0.499,d.getOffsetZ()*0.499);
            if(eye.distanceTo(end)>=c.player.getBlockInteractionRange()-0.15) continue;
            var h=c.world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,c.player));
            if(h.getType()==HitResult.Type.BLOCK && h.getBlockPos().equals(pos(cleanupTarget)) && h.getSide()==d) return true;
        }
        return false;
    }
    private static boolean align(MinecraftClient c,Cell goal) {
        if(at(c,goal)) return true;
        if(moveGoal!=null) { walk(c,moveGoal); return false; }
        Cell start=feet(c);
        if(start.equals(goal)) return walk(c,goal);
        var route=LayerMiningPlanner.route(walkingGrid(c),bounds,start,p->p.equals(goal),Set.of(),Math.min(start.y(),goal.y()));
        if(route==null || route.isEmpty()) { stop(c,"Kein freier Laufweg zum Randeinstieg."); return false; }
        if(route.getFirst().to().y()!=start.y()) { stop(c,"Zum Randeinstieg wird eine ebene Standflaeche benoetigt."); return false; }
        walk(c,route.getFirst().to()); return false;
    }
    private static boolean at(MinecraftClient c,Cell p) {
        return feet(c).y()==p.y() && Math.hypot(c.player.getX()-p.x()-0.5,c.player.getZ()-p.z()-0.5)<0.12;
    }
    private static boolean walk(MinecraftClient c,Cell target) {
        if(at(c,target)) { moveGoal=null; moveTicks=0; return true; }
        if(!clear(c,target) || !clear(c,target.add(0,1,0)) || !support(c,target.add(0,-1,0))) {
            stop(c,"Geplante Bahn blockiert oder ohne festen Boden."); return false;
        }
        if(!target.equals(moveGoal)) { moveGoal=target; bestDistance=Double.MAX_VALUE; moveTicks=0; }
        double dx=target.x()+0.5-c.player.getX(),dz=target.z()+0.5-c.player.getZ(),distance=Math.hypot(dx,dz);
        if(distance<bestDistance-0.01) { bestDistance=distance; moveTicks=0; }
        else if(++moveTicks>60) { stop(c,"Kein Fortschritt auf der geplanten Bahn."); return false; }
        float yaw=(float)Math.toDegrees(Math.atan2(dz,dx))-90;
        float diff=MathHelper.wrapDegrees(yaw-c.player.getYaw());
        c.player.setYaw(c.player.getYaw()+diff*0.4f); c.player.setPitch(c.player.getPitch()*0.6f);
        if(Math.abs(diff)<8) { c.options.forwardKey.setPressed(true); moving=true; }
        return false;
    }
    private static boolean panel(MinecraftClient c,Cell center,Axis normal,boolean down) {
        // A final single layer is struck from above so the 3x3 tool cannot cut the floor below the selection.
        if(top==bottom && normal!=Axis.Y) return panel(c,new Cell(center.x(),bottom,center.z()),Axis.Y,false);
        var plane=ThreeByThreePattern.footprint(center,normal);
        List<Cell> remaining=new ArrayList<>();
        if(targetInBand(c,center)) remaining.add(center);
        for(Cell p:plane) {
            var s=c.world.getBlockState(pos(p));
            if(!s.getFluidState().isEmpty()) { stop(c,"Fluessigkeit in der geplanten 3x3-Flaeche."); return false; }
            if(!p.equals(center) && targetInBand(c,p)) remaining.add(p);
        }
        if(remaining.isEmpty()) { breaking=null; c.interactionManager.cancelBlockBreaking(); return true; }
        if(breaking!=null && remaining.remove(breaking)) remaining.addFirst(breaking);
        for(Cell p:remaining) {
            if(c.world.getBlockState(pos(p)).getHardness(c.world,pos(p))<0 || !allowed(c,p,normal,down)) continue;
            var visible=hit(c,p,normal,down);
            if(visible!=null) { mine(c,p,visible,normal,down); blockedTicks=0; return false; }
        }
        // Keep the same panel and lane index; change only the struck face for edge remnants.
        if(!down) for(Cell p:remaining) for(Axis alternate:Axis.values()) {
            if(alternate==normal || !allowed(c,p,alternate,false)) continue;
            var visible=hit(c,p,alternate,false);
            if(visible!=null) { mine(c,p,visible,alternate,false); blockedTicks=0; return false; }
        }
        if(++blockedTicks>20) {
            if(down) { stop(c,"Kein erreichbarer Block im senkrechten Einstieg."); return false; }
            // Try recovering this panel; unreachable remnants are skipped by cleanup.
            recoveryPanel=List.copyOf(remaining); resumePhase=phase; resumePosition=feet(c);
            returningToLane=false; phase=Phase.CLEANUP; cleanupTarget=null; moveGoal=null; breaking=null;
            blockedTicks=breakTicks=0; release(c); c.interactionManager.cancelBlockBreaking();
        }
        return false;
    }
    private static boolean allowed(MinecraftClient c,Cell p,Axis normal,boolean down) {
        Cell floor=feet(c).add(0,-1,0);
        return ThreeByThreePattern.safeFootprint(p,normal,
            q -> breakable(c,q),
            q -> bounds.contains(q) && q.y()>=bottom && q.y()<=(phase==Phase.CLEANUP?bounds.maxY():top) && (down || !q.equals(floor)),
            q -> !c.world.getBlockState(pos(q)).getFluidState().isEmpty());
    }
    private static boolean breakable(MinecraftClient c,Cell p) {
        var s=c.world.getBlockState(pos(p));
        return !s.isAir() && s.getFluidState().isEmpty() && s.getHardness(c.world,pos(p))>=0;
    }
    private static boolean targetInBand(MinecraftClient c,Cell p) {
        return !skipped.contains(p) && bounds.contains(p) && p.y()>=bottom && p.y()<=top && breakable(c,p);
    }
    private static BlockHitResult hit(MinecraftClient c,Cell p,Axis normal,boolean down) {
        Vec3d eye=c.player.getEyePos(),center=pos(p).toCenterPos();
        for(Direction d:Direction.values()) {
            if(!d.getAxis().name().equals(normal.name()) || (down && d!=Direction.UP)) continue;
            Vec3d end=center.add(d.getOffsetX()*0.499,d.getOffsetY()*0.499,d.getOffsetZ()*0.499);
            var h=c.world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,c.player));
            if(h.getType()==HitResult.Type.BLOCK && h.getBlockPos().equals(pos(p)) && h.getSide()==d
                && eye.distanceTo(h.getPos())<c.player.getBlockInteractionRange()-0.05) return h;
        }
        return null;
    }
    private static void mine(MinecraftClient c,Cell p,BlockHitResult visible,Axis normal,boolean down) {
        if(!p.equals(breaking)) { breaking=p; breakTicks=0; c.interactionManager.cancelBlockBreaking(); }
        Vec3d delta=visible.getPos().subtract(c.player.getEyePos());
        double horizontal=Math.hypot(delta.x,delta.z);
        float yaw=horizontal<0.05?c.player.getYaw():(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90;
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,horizontal));
        c.player.setYaw(c.player.getYaw()+MathHelper.wrapDegrees(yaw-c.player.getYaw())*0.4f);
        c.player.setPitch(c.player.getPitch()+(pitch-c.player.getPitch())*0.4f);
        Vec3d eye=c.player.getEyePos();
        var actual=c.world.raycast(new RaycastContext(eye,eye.add(c.player.getRotationVec(1).multiply(c.player.getBlockInteractionRange())),
            RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,c.player));
        if(actual.getType()==HitResult.Type.BLOCK && actual.getBlockPos().equals(pos(p))
            && actual.getSide()==visible.getSide() && allowed(c,p,normal,down)) {
            c.interactionManager.updateBlockBreakingProgress(pos(p),actual.getSide()); c.player.swingHand(Hand.MAIN_HAND);
        } else c.interactionManager.cancelBlockBreaking();
        if(++breakTicks>600) stop(c,"Block seit 30 Sekunden nicht abgebaut.");
    }
}
