package com.bame.client.module;

import java.util.*;
import static com.bame.client.module.LayerMiningPlanner.*;

/** Dependency-free regression tests, run by the plannerTest Gradle task. */
public final class LayerMiningPlannerTest {
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static final class World implements Grid {
        Set<Cell> solid = new HashSet<>(), hazards = new HashSet<>();
        public boolean clear(Cell c) { return !solid.contains(c) && !hazards.contains(c); }
        public boolean support(Cell c) { return solid.contains(c) && !hazards.contains(c); }
        public boolean diggable(Cell c) { return solid.contains(c) && !hazards.contains(c); }
    }
    static World filled(int height) {
        World w = new World();
        for(int x=-1;x<=12;x++) for(int z=-1;z<=3;z++) w.solid.add(new Cell(x,-1,z));
        for(int x=0;x<=11;x++) for(int z=0;z<=2;z++) for(int y=0;y<height;y++) w.solid.add(new Cell(x,y,z));
        return w;
    }
    static void climb(int height) {
        World w = filled(height); Bounds b = new Bounds(0,0,0,11,height-1,2);
        Cell current = new Cell(-1,0,1); int destination=bottomOfBand(height-1,0);
        for(int attempt=0;current.y()!=destination && attempt<40;attempt++) {
            var path=route(w,b,current,p->p.y()==destination,Set.of(),0);
            check(path!=null && !path.isEmpty(), "No stair for height "+height);
            Step step=path.getFirst();
            check(step.to().y()-current.y()<=1,"Jump exceeds one block");
            for(Cell c:step.clearance()) if(!w.clear(c)) {
                check(b.contains(c),"Excavation outside selection");
                check(!c.equals(current.add(0,-1,0)),"Mines own support");
                w.solid.remove(c);
            }
            check(w.support(step.to().add(0,-1,0)),"Destination lost support");
            current=step.to();
        }
        check(current.y()==destination,"Failed to reach upper pair");
        // Simulate completed pairs, then execute live-world descent plans to the next pair.
        int top=height-1;
        while(top>=0) {
            int bottom=bottomOfBand(top,0);
            for(int attempt=0;current.y()!=bottom && attempt<40;attempt++) {
                var path=route(w,b,current,p->p.y()==bottom,Set.of(),bottom);
                check(path!=null && !path.isEmpty(),"No descent for height "+height);
                Step step=path.getFirst();
                check(current.y()-step.to().y()<=2,"Unsafe drop");
                for(Cell c:step.clearance()) if(!w.clear(c)) {
                    check(b.contains(c),"Descent digs outside selection");
                    w.solid.remove(c);
                }
                check(w.support(step.to().add(0,-1,0)),"Descent removes destination support");
                current=step.to();
            }
            check(current.y()==bottom,"Did not reach next pair");
            for(int x=0;x<=11;x++) for(int z=0;z<=2;z++) for(int y=top;y>=bottom;y--)
                w.solid.remove(new Cell(x,y,z));
            check(w.support(current.add(0,-1,0)),"Band removal mines supporting layer");
            top=bottom-1;
        }
        check(w.solid.stream().noneMatch(b::contains),"Selection not fully cleared");
    }
    public static void main(String[] args) {
        // An unreachable upper pillar must not prevent discovery of the next band.
        Bounds skipBounds=new Bounds(0,0,0,5,5,5);
        Set<Cell> blocks=new HashSet<>(), skipped=new HashSet<>();
        for(int y=3;y<=5;y++) { Cell p=new Cell(5,y,5); blocks.add(p); skipped.add(p); }
        Cell nextLayerBlock=new Cell(2,2,2); blocks.add(nextLayerBlock);
        check(ThreeByThreePattern.remnants(skipBounds,3,p->blocks.contains(p)&&!skipped.contains(p)).isEmpty(),
            "Skipped pillar prevents finishing upper band");
        check(ThreeByThreePattern.remnants(skipBounds,0,p->blocks.contains(p)&&!skipped.contains(p)).equals(List.of(nextLayerBlock)),
            "Skipping upper pillar hides lower layer or retries old pillar");
        check(skipped.stream().filter(blocks::contains).count()==3,"Skipped blocks must remain reportable");
        skipped.clear();
        check(ThreeByThreePattern.remnants(skipBounds,3,p->blocks.contains(p)&&!skipped.contains(p)).size()==3,
            "New run must retry previously skipped blocks");
        // Recovery can walk to a corner pillar and return to the saved lane position without digging a new lane.
        World cornerWorld=new World();
        Bounds cornerBounds=new Bounds(0,0,0,4,2,4);
        for(int x=0;x<=4;x++) for(int z=0;z<=4;z++) cornerWorld.solid.add(new Cell(x,-1,z));
        for(int y=0;y<=3;y++) for(int n=0;n<=4;n++) {
            cornerWorld.solid.add(new Cell(4,y,n)); cornerWorld.solid.add(new Cell(n,y,4));
        }
        for(int y=0;y<3;y++) cornerWorld.solid.add(new Cell(3,y,3));
        Grid recoveryGrid=new Grid() {
            public boolean clear(Cell p) { return cornerWorld.clear(p); }
            public boolean support(Cell p) { return p.y()==-1 && cornerWorld.support(p); }
            public boolean diggable(Cell p) { return false; }
        };
        Cell lanePosition=new Cell(0,0,0), visibleStand=new Cell(2,0,3);
        var approach=route(recoveryGrid,cornerBounds,lanePosition,p->p.equals(visibleStand),Set.of(),0);
        check(approach!=null && !approach.isEmpty(),"Cannot recover corner pillar from outside reach");
        for(Step s:approach) {
            check(s.to().y()==0,"Recovery leaves the current layer");
            for(Cell p:s.clearance()) check(cornerWorld.clear(p),"Recovery digs an unrelated wall");
        }
        for(int y=0;y<3;y++) cornerWorld.solid.remove(new Cell(3,y,3));
        var returnToLane=route(recoveryGrid,cornerBounds,visibleStand,p->p.equals(lanePosition),Set.of(),0);
        check(returnToLane!=null && returnToLane.getLast().to().equals(lanePosition),"Recovery cannot return to fixed lane");
        Bounds cleanupBox=new Bounds(-4,-3,-7,5,5,2);
        for(int x:new int[]{cleanupBox.minX(),cleanupBox.maxX()}) for(int z:new int[]{cleanupBox.minZ(),cleanupBox.maxZ()}) {
            Set<Cell> pillar=new HashSet<>();
            for(int y=-3;y<=5;y++) pillar.add(new Cell(x,y,z));
            var found=ThreeByThreePattern.remnants(cleanupBox,-3,pillar::contains);
            check(found.size()==9 && new HashSet<>(found).equals(pillar),"Missed corner pillar in completion scan");
            check(ThreeByThreePattern.remnants(cleanupBox,3,pillar::contains).size()==3,"Cleanup enters an unprocessed lower layer");
            check(ThreeByThreePattern.remnants(cleanupBox,0,pillar::contains).size()==6,"Older upper-layer remnant ignored");
            for(Cell p:found) pillar.remove(p);
            check(ThreeByThreePattern.remnants(cleanupBox,-3,pillar::contains).isEmpty(),"Cleanup did not finish");
            pillar.add(new Cell(x,5,z));
            check(ThreeByThreePattern.remnants(cleanupBox,-3,pillar::contains).size()==1,"Late server block update ignored");
        }
        // A one-wide remnant next to bedrock must still be mined, even with a missing middle.
        Cell middle=new Cell(0,1,0), lower=new Cell(0,0,0), upper=new Cell(0,2,0), floor=new Cell(0,-1,0);
        Set<Cell> remnants=new HashSet<>(Set.of(lower,upper,floor));
        java.util.function.Predicate<Cell> inside=p->p.x()==0 && p.z()==0 && p.y()>=0 && p.y()<=2;
        check(ThreeByThreePattern.safeFootprint(middle,ThreeByThreePattern.Axis.Z,remnants::contains,inside,p->false),
            "Unbreakable neighbours must not reject a narrow wall");
        check(!ThreeByThreePattern.safeFootprint(lower,ThreeByThreePattern.Axis.Z,remnants::contains,inside,p->false),
            "Must protect breakable floor under a residual block");
        check(ThreeByThreePattern.safeFootprint(lower,ThreeByThreePattern.Axis.Y,remnants::contains,inside,p->false),
            "Residual block can be struck from above without touching floor");
        remnants.add(new Cell(1,1,0));
        check(!ThreeByThreePattern.safeFootprint(middle,ThreeByThreePattern.Axis.Z,remnants::contains,inside,p->false),
            "Breakable neighbour outside selection must remain protected");
        remnants.remove(new Cell(1,1,0));
        check(!ThreeByThreePattern.safeFootprint(middle,ThreeByThreePattern.Axis.Z,remnants::contains,inside,p->p.equals(upper)),
            "Fluid hazard must still block mining");
        for(int mask=1;mask<512;mask++) {
            var plane=ThreeByThreePattern.footprint(middle,ThreeByThreePattern.Axis.Z);
            Set<Cell> sparse=new HashSet<>();
            for(int i=0;i<9;i++) if((mask & (1<<i))!=0) sparse.add(plane.get(i));
            check(ThreeByThreePattern.safeFootprint(middle,ThreeByThreePattern.Axis.Z,sparse::contains,plane::contains,p->false),
                "Incomplete panel rejected, mask="+mask);
        }
        for(int width:new int[]{1,2,3,4,6,7,9,12}) for(int depth:new int[]{1,2,3,5,6,9})
            for(boolean alongX:new boolean[]{true,false}) for(int corner=0;corner<4;corner++)
                laneCoverage(width,depth,alongX,corner);
        Cell origin=new Cell(-7,12,25);
        for(var axis:ThreeByThreePattern.Axis.values()) {
            var plane=ThreeByThreePattern.footprint(origin,axis);
            check(plane.size()==9 && new HashSet<>(plane).size()==9,"3x3 must contain exactly nine blocks");
            check(plane.contains(origin),"Missing center block");
            for(Cell p:plane) check(switch(axis) {
                case X -> p.x()==origin.x();
                case Y -> p.y()==origin.y();
                case Z -> p.z()==origin.z();
            },"Footprint is not a single plane");
        }
        Bounds negative=new Bounds(-10,-12,-20,-2,1,-12);
        check(ThreeByThreePattern.centerPenalty(new Cell(-9,0,-19),ThreeByThreePattern.Axis.Z,negative,1)==0,"Wall center not anchored to top");
        check(ThreeByThreePattern.centerPenalty(new Cell(-9,1,-19),ThreeByThreePattern.Axis.Y,negative,1)==0,"Top plane center incorrect");
        for(int height:new int[]{1,2,3,4,6,7,9,10}) {
            Set<Integer> covered=new HashSet<>();
            for(int top=height-1;top>=0;) {
                int bottom=ThreeByThreePattern.bottom(top,0);
                check(top-bottom<3,"3x3 band too tall");
                for(int y=bottom;y<=top;y++) check(covered.add(y),"Repeated 3x3 layer");
                top=bottom-1;
            }
            check(covered.size()==height,"3x3 skipped residual layers");
        }
        for(int height:new int[]{1,2,3,4,7,10}) {
            int top=height-1; Set<Integer> layers=new HashSet<>();
            while(top>=0) {
                int bottom=bottomOfBand(top,0);
                check(top-bottom<2,"Band taller than two");
                for(int y=top;y>=bottom;y--) check(layers.add(y),"Duplicate layer");
                top=bottom-1;
            }
            check(layers.size()==height,"Skipped a layer");
            climb(height);
        }
        World w = new World(); Bounds b=new Bounds(0,0,0,3,4,0);
        Cell start=new Cell(-1,0,0);
        w.solid.add(start.add(0,-1,0));
        check(route(w,b,start,p->p.x()==2,Set.of(),0)==null,"Walks over unsupported gap");
        w.solid.add(new Cell(0,-1,0)); w.hazards.add(new Cell(0,0,0));
        check(route(w,b,start,p->p.x()==0,Set.of(),0)==null,"Walks into fluid/hazard");
        World descent=filled(2);
        var down=route(descent,new Bounds(0,0,0,11,3,2),new Cell(0,2,1),p->p.y()==0,Set.of(),0);
        check(down!=null && !down.isEmpty(),"Cannot descend into next pair");
        check(down.getFirst().clearance().contains(new Cell(down.getFirst().to().x(),3,down.getFirst().to().z())),"Missing swept descent headroom");
        World ceiling=filled(4);
        ceiling.solid.add(new Cell(-1,2,1));
        var blocked=route(ceiling,new Bounds(0,0,0,11,3,2),new Cell(-1,0,1),p->p.y()==2,Set.of(),0);
        if(blocked!=null) for(Step step:blocked) for(Cell c:step.clearance())
            check(ceiling.clear(c) || new Bounds(0,0,0,11,3,2).contains(c),"Digs external ceiling");
        System.out.println("PASS: normal-layer paths and descent; 3x3 planar footprints on all axes, negative-coordinate centers and three-layer coverage");
    }

    static void laneCoverage(int width,int depth,boolean alongX,int corner) {
        Bounds b=new Bounds(-12,0,-7,-12+width-1,2,-7+depth-1);
        Cell near=new Cell((corner&1)==0?b.minX():b.maxX(),3,(corner&2)==0?b.minZ():b.maxZ());
        var plan=ThreeByThreeLanes.create(b,0,alongX,near);
        check(plan.equals(ThreeByThreeLanes.create(b,0,alongX,near)),"Lane planning must be deterministic");
        Set<Cell> removed=new HashSet<>();
        for(int y=0;y<=2;y++) removed.addAll(ThreeByThreePattern.footprint(new Cell(plan.entry().x(),y,plan.entry().z()),ThreeByThreePattern.Axis.Y));
        Cell previous=plan.entry();
        for(var action:plan.sweep()) {
            if(action.walk()) {
                Cell next=action.center();
                check(Math.abs(next.x()-previous.x())+Math.abs(next.z()-previous.z())<=1,"Non-cardinal/discontinuous lane step");
                check(next.y()==previous.y(),"Changes layer before finishing lanes");
                check(removed.contains(next) && removed.contains(next.add(0,1,0)),"Walks into uncleared wall");
                previous=next;
            } else {
                check(action.center().y()==1,"Not targeting vertical 3x3 middle");
                removed.addAll(ThreeByThreePattern.footprint(action.center(),action.normal()));
            }
        }
        for(int x=b.minX();x<=b.maxX();x++) for(int z=b.minZ();z<=b.maxZ();z++) for(int y=0;y<3;y++)
            check(removed.contains(new Cell(x,y,z)),"Lane coverage missing "+width+"x"+depth+" axis="+alongX+" corner="+corner+" at "+x+","+y+","+z);
        Set<Cell> fromSide=new HashSet<>();
        previous=plan.outside();
        for(var action:plan.sideEntry()) {
            if(!action.walk()) fromSide.addAll(ThreeByThreePattern.footprint(action.center(),action.normal()));
            else {
                check(Math.abs(action.center().x()-previous.x())+Math.abs(action.center().z()-previous.z())<=1,"Side entry skips a movement step");
                if(b.contains(action.center())) check(fromSide.contains(action.center()),"Side entry walks into solid wall");
                previous=action.center();
            }
        }
        check(previous.equals(plan.entry()),"Side entry does not end at lane origin");
    }
}
