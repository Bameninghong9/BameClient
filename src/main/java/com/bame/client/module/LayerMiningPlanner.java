package com.bame.client.module;

import java.util.*;
import java.util.function.Predicate;

/** Pure voxel planner: one-block stairs, two-block descents, no block placement. */
public final class LayerMiningPlanner {
    public record Cell(int x, int y, int z) {
        public Cell add(int dx, int dy, int dz) { return new Cell(x + dx, y + dy, z + dz); }
    }
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(Cell c) {
            return c.x >= minX && c.x <= maxX && c.y >= minY && c.y <= maxY && c.z >= minZ && c.z <= maxZ;
        }
    }
    public interface Grid {
        boolean clear(Cell c);
        boolean diggable(Cell c);
        boolean support(Cell c);
    }
    public record Step(Cell from, Cell to, List<Cell> clearance) {}
    public static int bottomOfBand(int top, int minimum) { return Math.max(minimum, top - 1); }

    public static List<Cell> clearance(Cell from, Cell to) {
        LinkedHashSet<Cell> cells = new LinkedHashSet<>();
        // Clear the whole swept headroom, including the source when jumping up.
        if (to.y > from.y) cells.add(from.add(0, 2, 0));
        for (int y = Math.max(from.y, to.y) + 1; y >= to.y; y--)
            cells.add(new Cell(to.x, y, to.z));
        return List.copyOf(cells);
    }

    public static List<Step> route(Grid grid, Bounds bounds, Cell start, Predicate<Cell> goal,
                                    Set<Cell> excluded, int minimumY) {
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Map<Cell, Step> previous = new HashMap<>();
        queue.add(start); previous.put(start, null);
        int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!queue.isEmpty() && previous.size() <= 8192) {
            Cell current = queue.remove();
            if (goal.test(current)) {
                ArrayList<Step> result = new ArrayList<>();
                for (Cell c = current; !c.equals(start); ) {
                    Step step = previous.get(c); result.add(step); c = step.from;
                }
                Collections.reverse(result); return result;
            }
            for (int[] d : directions) for (int dy : new int[]{0,1,-1,-2}) {
                Cell next = current.add(d[0], dy, d[1]);
                if (previous.containsKey(next) || excluded.contains(next)
                    || next.x < Math.min(bounds.minX - 1, start.x) || next.x > Math.max(bounds.maxX + 1, start.x)
                    || next.z < Math.min(bounds.minZ - 1, start.z) || next.z > Math.max(bounds.maxZ + 1, start.z)
                    || next.y < minimumY || next.y > Math.max(bounds.maxY, start.y)
                    || Math.abs(next.x - start.x) + Math.abs(next.z - start.z) > 64
                    || !grid.support(next.add(0,-1,0))) continue;
                List<Cell> swept = clearance(current, next);
                boolean valid = true;
                for (Cell c : swept) {
                    if (!grid.clear(c) && !(bounds.contains(c) && grid.diggable(c))) { valid = false; break; }
                }
                if (!valid) continue;
                previous.put(next, new Step(current, next, swept)); queue.add(next);
            }
        }
        return null;
    }
}
