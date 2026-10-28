package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Joins vanilla single-chest halves into double chests.
 *
 * Rule (vanilla 1.7.10): two chests form a double chest when they are the same block type (a chest never joins a
 * trapped chest) and touch horizontally. Vanilla never lets three chests touch, but a world edited by other tools
 * can contain that, so pairing is greedy and deterministic: chests are visited in position order (y, x, z) and each
 * pairs with its first unpaired neighbour in that same order.
 */
public final class ChestPairing {

    private ChestPairing() {}

    /**
     * @param chests position → block-type key (any object; equal keys mean the same chest block)
     * @return position → partner position, containing both directions for each pair. Unpaired chests are absent.
     */
    public static Map<BlockPos, BlockPos> pair(Map<BlockPos, ?> chests) {
        TreeMap<BlockPos, Object> ordered = new TreeMap<>(chests);
        Map<BlockPos, BlockPos> partners = new HashMap<>();
        for (Map.Entry<BlockPos, Object> e : ordered.entrySet()) {
            BlockPos p = e.getKey();
            if (partners.containsKey(p)) continue;
            for (BlockPos n : neighboursInOrder(p)) {
                if (!partners.containsKey(n) && ordered.containsKey(n)
                    && Objects.equals(ordered.get(n), e.getValue())) {
                    partners.put(p, n);
                    partners.put(n, p);
                    break;
                }
            }
        }
        return partners;
    }

    private static List<BlockPos> neighboursInOrder(BlockPos p) {
        List<BlockPos> n = new ArrayList<>(4);
        n.add(new BlockPos(p.x - 1, p.y, p.z));
        n.add(new BlockPos(p.x, p.y, p.z - 1));
        n.add(new BlockPos(p.x, p.y, p.z + 1));
        n.add(new BlockPos(p.x + 1, p.y, p.z));
        n.sort(null);
        return n;
    }
}
