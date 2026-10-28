package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Combines partial stacks of the same item inside one chest, so each item uses as few slots as it can (total ÷ max
 * stack, rounded up). For each item, the latest partial stack is poured into the earliest one until at most one
 * partial stack is left.
 *
 * One {@link Op} is what the client does with three left-clicks in one tick: pick up {@code from}, click {@code to}
 * (vanilla merges up to the stack limit; anything left stays on the cursor), click {@code from} again to put the rest
 * back. Deterministic: the same chest gives the same ops.
 */
public final class Combiner {

    private Combiner() {}

    public static final class Op {

        public final int from, to, key, moved;

        public Op(int from, int to, int key, int moved) {
            this.from = from;
            this.to = to;
            this.key = key;
            this.moved = moved;
        }

        @Override
        public String toString() {
            return moved + " of key " + key + " from slot " + from + " into slot " + to;
        }
    }

    /** Applies the combining to {@code m} and returns the ops that do it, in order. */
    public static List<Op> combine(ChestModel m, Map<Integer, ItemInfo> items) {
        Map<Integer, List<Integer>> partials = new TreeMap<>();
        for (int s = 0; s < m.size(); s++) {
            int k = m.keys[s];
            if (k == ChestModel.EMPTY) continue;
            if (m.counts[s] < limit(m, items, k)) partials.computeIfAbsent(k, x -> new ArrayList<Integer>())
                .add(s);
        }
        List<Op> ops = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> e : partials.entrySet()) {
            int key = e.getKey(), limit = limit(m, items, key);
            List<Integer> slots = e.getValue();
            int i = 0, j = slots.size() - 1;
            while (i < j) {
                int to = slots.get(i), from = slots.get(j);
                int move = Math.min(limit - m.counts[to], m.counts[from]);
                if (move <= 0) {
                    i++;
                    continue;
                }
                m.counts[to] += move;
                m.set(from, key, m.counts[from] - move);
                ops.add(new Op(from, to, key, move));
                if (m.keys[from] == ChestModel.EMPTY) j--;
                if (m.counts[to] >= limit) i++;
            }
        }
        return ops;
    }

    /** Stack limit for this item in this chest (the smaller of the item's and the slot's). */
    public static int limit(ChestModel m, Map<Integer, ItemInfo> items, int key) {
        ItemInfo info = items.get(key);
        int max = info == null ? 64 : Math.max(1, info.maxStack);
        return Math.min(max, m.slotLimit);
    }
}
