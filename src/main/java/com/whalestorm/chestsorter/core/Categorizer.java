package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Decides an item's category from its ore-dictionary names, mod id and creative tab.
 *
 * Rules, in order:
 * 1. For each ore-dictionary name, find its prefix: the longest known GT prefix the name starts with, where the next
 * character is uppercase, a digit, or the end ("ingotHotTitanium" → "ingotHot", "dustTinyIron" → "dustTiny").
 * If no known prefix matches, the leading lowercase run ("gemAmber" → "gem") is used when it is 2+ letters long.
 * 2. Any prefix starting with "ore" collapses to "ore" (GT has ~50 per-stone ore prefixes; one bucket for ores).
 * 3. Recipe-group prefixes ({@link #WEAK}: "list" from listAllseed, "crafting" from craftingPiston, ...) are ignored:
 * they group unrelated items. An item with only such names falls through to rule 5.
 * 4. Among the remaining names, the longest prefix wins; ties go to the alphabetically first prefix.
 * 5. No usable ore-dictionary name → "mod:" + mod id, narrowed by creative tab when there is one
 * ("mod:minecraft:redstone", "mod:gregtech:gregtech.main"). No mod id either → "misc".
 *
 * Pure and deterministic: the same inputs always give the same category.
 */
public final class Categorizer {

    public static final String MISC = "misc";

    /** Prefixes that name a recipe group rather than a kind of item. */
    public static final Set<String> WEAK = new TreeSet<>(
        java.util.Arrays.asList("list", "crafting", "craft", "item", "material", "component", "any", "all"));

    /** Known prefixes, longest first so the first match is the longest. */
    private final List<String> prefixes;

    public Categorizer(Collection<String> knownPrefixes) {
        Set<String> unique = new TreeSet<>();
        for (String p : knownPrefixes) {
            if (p != null && !p.trim()
                .isEmpty() && !p.startsWith("#")) unique.add(p.trim());
        }
        prefixes = new ArrayList<>(unique);
        prefixes.sort((a, b) -> a.length() != b.length() ? b.length() - a.length() : a.compareTo(b));
    }

    public String categorize(Collection<String> oreNames, String modId) {
        return categorize(oreNames, modId, null);
    }

    public String categorize(Collection<String> oreNames, String modId, String creativeTab) {
        String best = null;
        if (oreNames != null) {
            for (String name : oreNames) {
                String p = prefixOf(name);
                if (p == null || WEAK.contains(p)) continue;
                if (best == null || p.length() > best.length()
                    || (p.length() == best.length() && p.compareTo(best) < 0)) {
                    best = p;
                }
            }
        }
        if (best != null) return best;
        if (modId != null && !modId.trim()
            .isEmpty()) {
            String mod = "mod:" + modId.trim()
                .toLowerCase(Locale.ROOT);
            if (creativeTab != null && !creativeTab.trim()
                .isEmpty()) {
                return mod + ":"
                    + creativeTab.trim()
                        .toLowerCase(Locale.ROOT);
            }
            return mod;
        }
        return MISC;
    }

    /** @return the category prefix of one ore-dictionary name, or null if it has none. */
    public String prefixOf(String name) {
        if (name == null || name.isEmpty()) return null;
        String found = null;
        for (String p : prefixes) {
            if (name.startsWith(p) && boundaryAt(name, p.length())) {
                found = p;
                break;
            }
        }
        if (found == null) {
            int i = 0;
            while (i < name.length() && Character.isLowerCase(name.charAt(i))) i++;
            if (i >= 2 && boundaryAt(name, i)) found = name.substring(0, i);
        }
        if (found == null) return null;
        return found.startsWith("ore") ? "ore" : found;
    }

    private static boolean boundaryAt(String name, int i) {
        if (i == name.length()) return true;
        char c = name.charAt(i);
        return Character.isUpperCase(c) || Character.isDigit(c) || c == '_';
    }
}
