package com.whalestorm.chestsorter.core;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * What one container holds, slot by slot. Items are identified by an int key handed out by the client's item-key
 * registry (two stacks get the same key only if vanilla would let them merge: same item, damage and NBT).
 * {@link #EMPTY} marks an empty slot.
 */
public final class ChestModel {

    public static final int EMPTY = -1;

    /** Index into the scan result list; stable for one run. */
    public final int id;
    public final BlockPos pos;
    public final String name;
    /** Max items per slot imposed by the container (vanilla and Iron Chests: 64). */
    public final int slotLimit;
    public final int[] keys;
    public final int[] counts;

    public ChestModel(int id, BlockPos pos, String name, int slots, int slotLimit) {
        this.id = id;
        this.pos = pos;
        this.name = name;
        this.slotLimit = slotLimit;
        this.keys = new int[slots];
        this.counts = new int[slots];
        Arrays.fill(keys, EMPTY);
    }

    public int size() {
        return keys.length;
    }

    public void set(int slot, int key, int count) {
        if (count <= 0 || key == EMPTY) {
            keys[slot] = EMPTY;
            counts[slot] = 0;
        } else {
            keys[slot] = key;
            counts[slot] = count;
        }
    }

    public boolean isEmpty() {
        for (int k : keys) if (k != EMPTY) return false;
        return true;
    }

    public ChestModel copy() {
        ChestModel c = new ChestModel(id, pos, name, keys.length, slotLimit);
        System.arraycopy(keys, 0, c.keys, 0, keys.length);
        System.arraycopy(counts, 0, c.counts, 0, counts.length);
        return c;
    }

    /** Total count per key. Sorted for stable output. */
    public Map<Integer, Long> totals() {
        Map<Integer, Long> t = new TreeMap<>();
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != EMPTY) t.merge(keys[i], (long) counts[i], Long::sum);
        }
        return t;
    }

    /**
     * How many of {@code key} fit, given per-item max stack size, merging into partial stacks first and then empty
     * slots. Mirrors vanilla left-click placement limits (min of item max stack and slot limit).
     */
    public int roomFor(int key, int maxStack) {
        int limit = Math.min(maxStack, slotLimit);
        long room = 0;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == key) room += Math.max(0, limit - counts[i]);
            else if (keys[i] == EMPTY) room += limit;
        }
        return (int) Math.min(Integer.MAX_VALUE, room);
    }

    /**
     * Places {@code count} of {@code key} the same way the client deposit step does: top up existing partial stacks
     * in slot order, then fill empty slots in slot order. Caller must have checked {@link #roomFor}.
     *
     * @return the number that did not fit (0 when the caller checked room).
     */
    public int place(int key, int count, int maxStack) {
        int limit = Math.min(maxStack, slotLimit);
        int left = count;
        for (int i = 0; i < keys.length && left > 0; i++) {
            if (keys[i] == key && counts[i] < limit) {
                int add = Math.min(limit - counts[i], left);
                counts[i] += add;
                left -= add;
            }
        }
        for (int i = 0; i < keys.length && left > 0; i++) {
            if (keys[i] == EMPTY) {
                int add = Math.min(limit, left);
                keys[i] = key;
                counts[i] = add;
                left -= add;
            }
        }
        return left;
    }

    @Override
    public String toString() {
        return name + "#" + id + " at " + pos;
    }
}
