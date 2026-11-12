package com.whalestorm.chestsorter.core;

/** Facts about one item key that planning needs. Immutable. */
public final class ItemInfo {

    public final int key;
    /** Player-facing name, for chat and the trace file. */
    public final String label;
    public final String category;
    /** Item's own max stack size (1 for tools, 16 for some items, usually 64). */
    public final int maxStack;
    /** Mod id and creative-tab label as the game spells them (not lowercased), or null; for Logistics Pipes setup. */
    public final String modId, tab;
    /** Ore-dictionary names, or empty. */
    public final java.util.List<String> oreNames;

    public ItemInfo(int key, String label, String category, int maxStack) {
        this(key, label, category, maxStack, null, null, null);
    }

    public ItemInfo(int key, String label, String category, int maxStack, String modId, String tab,
        java.util.List<String> oreNames) {
        this.key = key;
        this.label = label;
        this.category = category;
        this.maxStack = Math.max(1, maxStack);
        this.modId = modId;
        this.tab = tab;
        this.oreNames = oreNames == null ? java.util.Collections.<String>emptyList()
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(oreNames));
    }

    @Override
    public String toString() {
        return label + " [" + category + "]";
    }
}
