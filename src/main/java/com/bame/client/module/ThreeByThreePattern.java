package com.bame.client.module;

import java.util.*;
import java.util.function.Predicate;
import static com.bame.client.module.LayerMiningPlanner.*;

/** A planar 3x3, never a 27-block cube. Axis is the normal of the struck face. */
public final class ThreeByThreePattern {
    public enum Axis { X, Y, Z }
    public static int bottom(int top, int minimum) { return Math.max(minimum, top-2); }
    /** Inclusive scan: corner/edge cells are just as important as aligned 3x3 centers. */
    public static List<Cell> remnants(Bounds b,int lowestProcessed,Predicate<Cell> breakable) {
        List<Cell> result=new ArrayList<>();
        for(int x=b.minX();x<=b.maxX();x++) for(int z=b.minZ();z<=b.maxZ();z++)
            for(int y=b.maxY();y>=Math.max(b.minY(),lowestProcessed);y--) {
                Cell p=new Cell(x,y,z);
                if(breakable.test(p)) result.add(p);
            }
        return result;
    }
    /** Air and unbreakable neighbours are not removed by a 3x3 pickaxe. */
    public static boolean safeFootprint(Cell center, Axis normal, Predicate<Cell> breakable,
                                       Predicate<Cell> permitted, Predicate<Cell> hazard) {
        for(Cell p:footprint(center,normal)) {
            if(hazard.test(p) || (breakable.test(p) && !permitted.test(p))) return false;
        }
        return true;
    }
    public static List<Cell> footprint(Cell center, Axis normal) {
        List<Cell> result=new ArrayList<>(9);
        for(int a=-1;a<=1;a++) for(int b=-1;b<=1;b++)
            result.add(switch(normal) {
                case X -> center.add(0,a,b);
                case Y -> center.add(a,0,b);
                case Z -> center.add(a,b,0);
            });
        return List.copyOf(result);
    }
    public static int centerPenalty(Cell p, Axis axis, Bounds bounds, int top) {
        int x=Math.abs(Math.floorMod(p.x()-bounds.minX(),3)-1);
        int z=Math.abs(Math.floorMod(p.z()-bounds.minZ(),3)-1);
        int y=Math.abs(p.y()-(top-1));
        return switch(axis) { case X -> y+z; case Y -> x+z; case Z -> y+x; };
    }
}
