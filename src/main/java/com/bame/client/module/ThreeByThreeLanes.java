package com.bame.client.module;

import java.util.*;
import static com.bame.client.module.LayerMiningPlanner.*;
import static com.bame.client.module.ThreeByThreePattern.Axis;

/** Immutable serpentine route. Only the initial corner depends on the player's position. */
public final class ThreeByThreeLanes {
    public record Action(Cell center, Axis normal, boolean walk) {}
    public record Plan(Cell entry, Cell outside, List<Action> sideEntry, List<Action> sweep) {}
    private static int low(int min,int max) { return max-min<2 ? (min+max)/2 : min+1; }
    private static int high(int min,int max) { return max-min<2 ? (min+max)/2 : max-1; }
    public static Plan create(Bounds b,int bottom,boolean alongX,Cell near) {
        int fMin=alongX?b.minX():b.minZ(), fMax=alongX?b.maxX():b.maxZ();
        int cMin=alongX?b.minZ():b.minX(), cMax=alongX?b.maxZ():b.maxX();
        int fLow=low(fMin,fMax), fHigh=high(fMin,fMax), cLow=low(cMin,cMax), cHigh=high(cMin,cMax);
        int nearF=alongX?near.x():near.z(), nearC=alongX?near.z():near.x();
        int f=Math.abs(nearF-fLow)<=Math.abs(nearF-fHigh)?fLow:fHigh;
        int cross=Math.abs(nearC-cLow)<=Math.abs(nearC-cHigh)?cLow:cHigh;
        int direction=f==fLow?1:-1, side=cross==cLow?1:-1;
        Cell entry=cell(f,cross,bottom,alongX);
        int boundary=direction>0?fMin:fMax;
        Cell outside=cell(boundary-direction,cross,bottom,alongX);
        Axis forwardNormal=alongX?Axis.X:Axis.Z, sideNormal=alongX?Axis.Z:Axis.X;
        List<Action> initial=new ArrayList<>(), sweep=new ArrayList<>();
        int midY=Math.min(bottom+1,b.maxY());
        // Side entry clears the same initial patch that a top entry excavates downward.
        for(int p=boundary; direction*(p-(f+direction))<=0; p+=direction) {
            if(p<fMin || p>fMax) break;
            initial.add(new Action(cell(p,cross,midY,alongX),forwardNormal,false));
            initial.add(new Action(cell(p-direction,cross,bottom,alongX),forwardNormal,true));
        }
        if(initial.isEmpty() || !initial.getLast().center().equals(entry)) initial.add(new Action(entry,forwardNormal,true));
        while(true) {
            int end=direction>0?fMax:fMin;
            for(int p=f+direction*2;direction*(p-end)<=0;p+=direction) {
                sweep.add(new Action(cell(p,cross,midY,alongX),forwardNormal,false));
                sweep.add(new Action(cell(p-direction,cross,bottom,alongX),forwardNormal,true));
                f=p-direction;
            }
            int sideEnd=side>0?cHigh:cLow;
            if(cross==sideEnd) break;
            int next=side>0?Math.min(cross+3,sideEnd):Math.max(cross-3,sideEnd);
            for(int p=cross+side*2;side*(p-(next+side))<=0;p+=side) {
                sweep.add(new Action(cell(f,p,midY,alongX),sideNormal,false));
                sweep.add(new Action(cell(f,p-side,bottom,alongX),sideNormal,true));
            }
            cross=next; direction=-direction;
        }
        return new Plan(entry,outside,List.copyOf(initial),List.copyOf(sweep));
    }
    private static Cell cell(int forward,int cross,int y,boolean alongX) {
        return alongX?new Cell(forward,y,cross):new Cell(cross,y,forward);
    }
}
