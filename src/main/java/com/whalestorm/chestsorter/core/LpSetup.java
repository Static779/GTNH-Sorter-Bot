package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns a finished chest assignment into a Logistics Pipes setup: for each chest that holds a group, which sink
 * modules on its pipe would keep sending new items of that group there. Read from the source of
 * GTNewHorizons/LogisticsPipes 1.5.36-GTNH (the version in GTNH 2.9.0-RC-2):
 * - Type Filter ItemSink: accepts items GregTech lists under any of its GT prefixes ("ingot", "plate", ...; "ore"
 * takes every ore-block prefix). Its list is filled by putting in an item, which adds that item's GT prefix.
 * - OreDict ItemSink: accepts items with any of the listed ore-dictionary names (exact names).
 * - ModBased ItemSink: accepts items from the listed mod ids. CreativeTab ItemSink: items on the listed tabs.
 * - Default Route: takes whatever no other sink wants (one per network, for the misc chest).
 * - Polymorphic ItemSink: accepts items the chest already holds; no setup, but a new kind of item has nowhere to go.
 * When several sinks accept an item, LP uses the highest kind: OreDict above Type Filter above ModBased and
 * CreativeTab above Default Route (SinkReply.FixedPriority).
 */
public final class LpSetup {

    private LpSetup() {}

    /** Setup for one chest. */
    public static final class ChestSetup {

        public final int chest;
        public final BlockPos pos;
        public final String group;
        /** GT prefix → an item to put in the Type Filter sink to add it. */
        public final Map<String, String> typeFilter = new TreeMap<>();
        public final Set<String> oreDict = new TreeSet<>();
        public final Set<String> mods = new TreeSet<>();
        public final Set<String> tabs = new TreeSet<>();
        public boolean defaultRoute;
        /** Categories no sink could be written for (cover them with a Polymorphic ItemSink). */
        public final Set<String> unmatched = new TreeSet<>();

        ChestSetup(int chest, BlockPos pos, String group) {
            this.chest = chest;
            this.pos = pos;
            this.group = group;
        }

        public List<String> lines() {
            List<String> out = new ArrayList<>();
            out.add("Chest #" + chest + " at " + pos.x + " " + pos.y + " " + pos.z + " [" + group + "]");
            if (!typeFilter.isEmpty()) {
                StringBuilder b = new StringBuilder("  Type Filter ItemSink: ");
                boolean first = true;
                for (Map.Entry<String, String> e : typeFilter.entrySet()) {
                    b.append(first ? "" : ", ")
                        .append(e.getKey())
                        .append(" (put in: ")
                        .append(e.getValue())
                        .append(")");
                    first = false;
                }
                out.add(b.toString());
            }
            if (!oreDict.isEmpty()) out.add("  OreDict ItemSink: " + String.join(", ", oreDict));
            if (!mods.isEmpty()) out.add("  ModBased ItemSink: " + String.join(", ", mods));
            if (!tabs.isEmpty()) out.add("  CreativeTab ItemSink: " + String.join(", ", tabs));
            if (defaultRoute) out.add("  Default Route (misc: anything no other sink takes)");
            if (!unmatched.isEmpty()) out.add("  No sink for: " + String.join(", ", unmatched) + " (add a Polymorphic ItemSink)");
            return out;
        }
    }

    /**
     * @param chests     every chest of the run (positions)
     * @param items      every item seen in the run
     * @param a          the assignment (chest → group, small categories merged into others)
     * @param gtPrefixes GT ore prefixes, as the categorizer knows them
     * @return one setup per chest that has a group, nearest the first chest's order
     */
    public static List<ChestSetup> build(List<ChestModel> chests, Collection<ItemInfo> items, Assigner.Result a,
        Collection<String> gtPrefixes) {
        Set<String> gt = new TreeSet<>(gtPrefixes);
        // group → fine category → items of it
        Map<String, Map<String, List<ItemInfo>>> byGroup = new TreeMap<>();
        for (ItemInfo info : items) {
            String g = a.effective(info.category);
            byGroup.computeIfAbsent(g, k -> new TreeMap<>())
                .computeIfAbsent(info.category, k -> new ArrayList<>())
                .add(info);
        }
        List<ChestSetup> out = new ArrayList<>();
        for (ChestModel c : chests) {
            String g = a.chestCategory.get(c.id);
            if (g == null) continue;
            ChestSetup s = new ChestSetup(c.id, c.pos, g);
            Map<String, List<ItemInfo>> fine = byGroup.get(g);
            if (fine == null) fine = new LinkedHashMap<>();
            if (fine.isEmpty() || g.equals(Taxonomy.MISC) || fine.containsKey(Categorizer.MISC)) s.defaultRoute = true;
            for (Map.Entry<String, List<ItemInfo>> e : fine.entrySet()) {
                String cat = e.getKey();
                List<ItemInfo> list = e.getValue();
                if (cat.equals(Categorizer.MISC)) continue;
                if (cat.startsWith("mod:")) {
                    // "mod:<id>" or "mod:<id>:<tab>": LP compares the game's own spelling, which ItemInfo keeps.
                    boolean byTab = cat.indexOf(':', 4) >= 0;
                    boolean any = false;
                    for (ItemInfo i : list) {
                        if (byTab && i.tab != null) any |= s.tabs.add(i.tab) || s.tabs.contains(i.tab);
                        else if (i.modId != null) any |= s.mods.add(i.modId) || s.mods.contains(i.modId);
                    }
                    if (!any) s.unmatched.add(cat);
                } else if (gt.contains(cat) || cat.equals("ore")) {
                    s.typeFilter.put(cat, list.get(0).label);
                } else {
                    // Not a GT prefix: list the exact ore names that start with it.
                    boolean any = false;
                    for (ItemInfo i : list) {
                        for (String n : i.oreNames) if (n.startsWith(cat)) any |= s.oreDict.add(n) || true;
                    }
                    if (!any) s.unmatched.add(cat);
                }
            }
            out.add(s);
        }
        return out;
    }

    /** The whole setup as text, with the header that explains it. */
    public static List<String> text(List<ChestSetup> setups) {
        List<String> out = new ArrayList<>();
        out.add("Logistics Pipes setup for the sorted chests (one pipe with a Chassis per chest).");
        out.add("Simplest: a Polymorphic ItemSink on every chest keeps each item going where it already is, plus a");
        out.add("Default Route on the misc chest for new kinds of items. For new items to find their group's chest,");
        out.add("use the sinks below instead. Sinks rank: OreDict > Type Filter > ModBased/CreativeTab > Default Route.");
        out.add("A group spread over several chests gets the same sinks on each.");
        out.add("");
        for (ChestSetup s : setups) out.addAll(s.lines());
        return out;
    }
}
