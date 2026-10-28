package com.whalestorm.chestsorter.core;

/** Facts about one item key that planning needs. Immutable. */
public final class ItemInfo {

    public final int key;
    /** Player-facing name, for chat and the trace file. */
    public final String label;
    public final String category;
    /** Item's own max stack size (1 for tools, 16 for some items, usually 64). */
    public final int maxStack;

    public ItemInfo(int key, String label, String category, int maxStack) {
        this.key = key;
        this.label = label;
        this.category = category;
        this.maxStack = Math.max(1, maxStack);
    }

    @Override
    public String toString() {
        return label + " [" + category + "]";
    }
}
