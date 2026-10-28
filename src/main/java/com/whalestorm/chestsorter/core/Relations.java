package com.whalestorm.chestsorter.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How related two categories are, from 0 (unrelated) to 1. Used to put related categories in nearby chests and to
 * pick which category a small one merges into when there are not enough chests.
 *
 * Sources, strongest first:
 * - Processing chains, in GT order (ore → rawOre → crushed → crushedPurified → ... → dust → dustTiny). Neighbours in
 * a chain score high, far ends low.
 * - Name nesting: "plateDouble" is a kind of "plate", "gemFlawless" a kind of "gem" (camel-case prefix).
 * - Same mod: "mod:minecraft:redstone" and "mod:minecraft:food".
 */
public final class Relations {

    private Relations() {}

    /** Chains of related categories, in the order they should sit next to each other. */
    static final List<List<String>> CHAINS = Arrays.asList(
        Arrays.asList(
            "ore",
            "rawOre",
            "crushed",
            "crushedPurified",
            "crushedCentrifuged",
            "dustImpure",
            "dustPure",
            "dustRefined",
            "dust",
            "dustSmall",
            "dustTiny"),
        Arrays.asList("ingotHot", "ingot", "nugget", "block"),
        Arrays.asList(
            "plate",
            "plateDouble",
            "plateTriple",
            "plateQuadruple",
            "plateQuintuple",
            "plateDense",
            "plateSuperdense",
            "plateAlloy",
            "foil",
            "itemCasing"),
        Arrays.asList(
            "stick",
            "stickLong",
            "bolt",
            "screw",
            "round",
            "ring",
            "spring",
            "springSmall",
            "gearGtSmall",
            "gearGt",
            "rotor",
            "frameGt"),
        Arrays.asList("gemChipped", "gemFlawed", "gem", "gemFlawless", "gemExquisite", "lens"),
        Arrays.asList(
            "wireFine",
            "wireGt01",
            "wireGt02",
            "wireGt04",
            "wireGt08",
            "wireGt12",
            "wireGt16",
            "cableGt01",
            "cableGt02",
            "cableGt04",
            "cableGt08",
            "cableGt12",
            "cableGt16"),
        Arrays.asList(
            "pipeTiny",
            "pipeSmall",
            "pipeMedium",
            "pipeLarge",
            "pipeHuge",
            "pipeQuadruple",
            "pipeNonuple",
            "pipeRestrictiveTiny",
            "pipeRestrictiveSmall",
            "pipeRestrictiveMedium",
            "pipeRestrictiveLarge",
            "pipeRestrictiveHuge"),
        Arrays.asList("cell", "cellMolten", "cellPlasma", "capsule", "capsuleMolten", "bucket", "bucketClay", "bottle"),
        Arrays.asList("craftingTool", "toolHeadFile", "toolHeadHammer", "toolHeadSaw", "toolHeadWrench", "tool"),
        Arrays.asList("log", "plank", "slab", "stair", "fence", "treeSapling", "treeLeaves", "wood"),
        Arrays.asList(
            "cobblestone",
            "stone",
            "stoneCobble",
            "stoneSmooth",
            "stoneBricks",
            "stoneCracked",
            "stoneMossy",
            "gravel",
            "sand",
            "sandstone",
            "dirt",
            "netherrack"),
        Arrays.asList("seed", "crop", "plant", "flower", "food", "dye"));

    private static final Map<String, int[]> INDEX = new HashMap<>();

    static {
        for (int c = 0; c < CHAINS.size(); c++) {
            List<String> chain = CHAINS.get(c);
            for (int i = 0; i < chain.size(); i++) INDEX.put(chain.get(i), new int[] { c, i, chain.size() });
        }
    }

    public static double score(String a, String b) {
        if (a == null || b == null) return 0;
        if (a.equals(b)) return 1;
        double best = 0;
        int[] ia = INDEX.get(a), ib = INDEX.get(b);
        if (ia != null && ib != null && ia[0] == ib[0]) {
            int gap = Math.abs(ia[1] - ib[1]);
            best = Math.max(best, Math.max(0.3, 1.0 - 0.15 * gap));
        }
        if (nested(a, b) || nested(b, a)) best = Math.max(best, 0.8);
        String ma = modOf(a), mb = modOf(b);
        if (ma != null && ma.equals(mb)) best = Math.max(best, 0.6);
        return best;
    }

    /** "plateDouble" is nested in "plate": starts with it and continues with an uppercase letter or digit. */
    static boolean nested(String inner, String outer) {
        if (inner.length() <= outer.length() || !inner.startsWith(outer)) return false;
        char c = inner.charAt(outer.length());
        return Character.isUpperCase(c) || Character.isDigit(c);
    }

    /** "mod:minecraft:redstone" → "minecraft"; non-mod categories → null. */
    static String modOf(String cat) {
        if (!cat.startsWith("mod:")) return null;
        int end = cat.indexOf(':', 4);
        return end < 0 ? cat.substring(4) : cat.substring(4, end);
    }
}
