package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* over block positions. A node is the block the player's feet are in.
 *
 * A node is standable when the block below is a floor and the feet and head blocks are both open.
 * Moves, to any of the 8 horizontal neighbours:
 * - level walk; diagonals only when both side-by blocks are open at feet and head height (no corner cutting)
 * - step up 1 block (jump), needing headroom above the current node
 * - drop 1 to {@link #MAX_DROP} blocks, with the whole fall column open
 * No ladders, swimming, doors or block breaking.
 */
public final class PathFinder {

    /** What the walker needs to know about the world. Implemented over the client world in the mod. */
    public interface Terrain {

        /** The player can occupy this block: no collision box, no liquid, no fire, web, portal or cactus. */
        boolean open(int x, int y, int z);

        /** The player can stand on top of this block: full-ish collision box, not harmful. */
        boolean floor(int x, int y, int z);
    }

    /** Decides when a node is close enough to the target. */
    public interface Goal {

        boolean reached(BlockPos feet);

        /** Lower bound on remaining cost; 0 is always safe. */
        double estimate(BlockPos feet);
    }

    public static final int MAX_DROP = 3;
    private static final double DIAG = Math.sqrt(2);

    private PathFinder() {}

    public static boolean standable(Terrain t, int x, int y, int z) {
        return t.floor(x, y - 1, z) && t.open(x, y, z) && t.open(x, y + 1, z);
    }

    /**
     * @return feet positions to walk through, not including the start; empty if the start already satisfies the
     *         goal; null if no path within {@code maxNodes} expanded nodes.
     */
    public static List<BlockPos> find(Terrain t, BlockPos start, Goal goal, int maxNodes) {
        if (goal.reached(start)) return new ArrayList<>();
        Map<BlockPos, Double> g = new HashMap<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        Map<Long, BlockPos> idx = new HashMap<>();
        long seq = 0;

        g.put(start, 0.0);
        idx.put(seq, start);
        open.add(new double[] { goal.estimate(start), seq++ });
        int expanded = 0;

        while (!open.isEmpty() && expanded < maxNodes) {
            BlockPos cur = idx.remove((long) open.poll()[1]);
            if (cur == null || !closed.add(cur)) continue;
            expanded++;
            if (goal.reached(cur)) return rebuild(parent, start, cur);
            double gc = g.get(cur);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    boolean diag = dx != 0 && dz != 0;
                    for (BlockPos[] step : neighbours(t, cur, dx, dz, diag)) {
                        BlockPos n = step[0];
                        if (closed.contains(n)) continue;
                        double cost = (diag ? DIAG : 1.0) + verticalCost(cur.y, n.y);
                        double ng = gc + cost;
                        Double old = g.get(n);
                        if (old != null && old <= ng) continue;
                        g.put(n, ng);
                        parent.put(n, cur);
                        idx.put(seq, n);
                        open.add(new double[] { ng + goal.estimate(n), seq++ });
                    }
                }
            }
        }
        return null;
    }

    private static double verticalCost(int fromY, int toY) {
        if (toY > fromY) return 1.0; // jump
        if (toY < fromY) return 0.5 * (fromY - toY);
        return 0;
    }

    private static List<BlockPos[]> neighbours(Terrain t, BlockPos c, int dx, int dz, boolean diag) {
        List<BlockPos[]> out = new ArrayList<>(1);
        int nx = c.x + dx, nz = c.z + dz;
        if (diag) {
            // Level moves only, and never through a corner.
            if (t.open(c.x + dx, c.y, c.z) && t.open(c.x + dx, c.y + 1, c.z)
                && t.open(c.x, c.y, c.z + dz)
                && t.open(c.x, c.y + 1, c.z + dz)
                && standable(t, nx, c.y, nz)) {
                out.add(new BlockPos[] { new BlockPos(nx, c.y, nz) });
            }
            return out;
        }
        if (standable(t, nx, c.y, nz)) {
            out.add(new BlockPos[] { new BlockPos(nx, c.y, nz) });
            return out;
        }
        // Step up: needs headroom above us for the jump.
        if (t.open(c.x, c.y + 2, c.z) && standable(t, nx, c.y + 1, nz)) {
            out.add(new BlockPos[] { new BlockPos(nx, c.y + 1, nz) });
            return out;
        }
        // Drop: walk off the edge into an open column.
        if (t.open(nx, c.y, nz) && t.open(nx, c.y + 1, nz)) {
            for (int d = 1; d <= MAX_DROP; d++) {
                if (standable(t, nx, c.y - d, nz)) {
                    out.add(new BlockPos[] { new BlockPos(nx, c.y - d, nz) });
                    break;
                }
                if (!t.open(nx, c.y - d, nz)) break;
            }
        }
        return out;
    }

    private static List<BlockPos> rebuild(Map<BlockPos, BlockPos> parent, BlockPos start, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos p = end; p != null && !p.equals(start); p = parent.get(p)) path.add(p);
        Collections.reverse(path);
        return path;
    }

    /** Goal: stand where the eye is within {@code reach} of the target block's centre. */
    public static Goal withinReach(final BlockPos target, final double reach, final double eyeHeight) {
        return new Goal() {

            @Override
            public boolean reached(BlockPos f) {
                if (f.x == target.x && f.z == target.z && (f.y == target.y || f.y == target.y + 1)) return false;
                double dx = f.x + 0.5 - (target.x + 0.5);
                double dy = f.y + eyeHeight - (target.y + 0.5);
                double dz = f.z + 0.5 - (target.z + 0.5);
                return dx * dx + dy * dy + dz * dz <= reach * reach;
            }

            @Override
            public double estimate(BlockPos f) {
                double dx = f.x - target.x, dy = f.y - target.y, dz = f.z - target.z;
                return Math.max(0, Math.sqrt(dx * dx + dy * dy + dz * dz) - reach - 1);
            }
        };
    }
}
