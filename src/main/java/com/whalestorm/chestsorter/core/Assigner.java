package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gives each chest a category.
 *
 * 1. A chest with items takes the category that fills the most of its slots (ties: alphabetically first).
 * Several chests may end up with the same category; all of them accept that category.
 * 2. Categories present somewhere but owned by no chest get an empty chest each, biggest category first
 * (by slots used across all chests, ties alphabetical). Empty chests are handed out in the order given,
 * which the caller makes nearest-to-player first.
 * 3. A category still without a chest is left unassigned: its items are not moved anywhere.
 *
 * Chests whose id is in {@code locked} keep the given category and skip rule 1.
 */
public final class Assigner {

    private Assigner() {}

    public static final class Result {

        /** chest id → category. Chests absent here are empty and unused. */
        public final Map<Integer, String> chestCategory;
        /** Categories with items but no chest; those items stay where they are. */
        public final List<String> unassigned;

        Result(Map<Integer, String> chestCategory, List<String> unassigned) {
            this.chestCategory = chestCategory;
            this.unassigned = unassigned;
        }
    }

    /**
     * @param chests in hand-out order for empty chests (nearest first)
     * @param items  key → info, must cover every key in the chests
     * @param locked chest id → forced category (may be empty)
     */
    public static Result assign(List<ChestModel> chests, Map<Integer, ItemInfo> items, Map<Integer, String> locked) {
        Map<Integer, String> result = new LinkedHashMap<>();
        Map<String, Integer> slotsPerCategory = new TreeMap<>();

        for (ChestModel c : chests) {
            Map<String, Integer> here = new TreeMap<>();
            for (int k : c.keys) {
                if (k == ChestModel.EMPTY) continue;
                String cat = info(items, k).category;
                here.merge(cat, 1, Integer::sum);
                slotsPerCategory.merge(cat, 1, Integer::sum);
            }
            if (locked.containsKey(c.id)) {
                result.put(c.id, locked.get(c.id));
            } else if (!here.isEmpty()) {
                result.put(c.id, majority(here));
            }
        }

        // Categories with no chest, biggest first.
        List<String> needed = new ArrayList<>();
        for (String cat : slotsPerCategory.keySet()) if (!result.containsValue(cat)) needed.add(cat);
        needed.sort((a, b) -> {
            int d = slotsPerCategory.get(b) - slotsPerCategory.get(a);
            return d != 0 ? d : a.compareTo(b);
        });

        List<ChestModel> empties = new ArrayList<>();
        for (ChestModel c : chests) if (!result.containsKey(c.id) && c.isEmpty()) empties.add(c);

        List<String> unassigned = new ArrayList<>();
        int e = 0;
        for (String cat : needed) {
            if (e < empties.size()) result.put(empties.get(e++).id, cat);
            else unassigned.add(cat);
        }
        return new Result(result, unassigned);
    }

    private static String majority(Map<String, Integer> counts) {
        String best = null;
        int bestN = -1;
        for (Map.Entry<String, Integer> en : counts.entrySet()) { // TreeMap: alphabetical, so first max wins ties
            if (en.getValue() > bestN) {
                best = en.getKey();
                bestN = en.getValue();
            }
        }
        return best;
    }

    static ItemInfo info(Map<Integer, ItemInfo> items, int key) {
        ItemInfo i = items.get(key);
        if (i == null) throw new IllegalArgumentException("No item info for key " + key);
        return i;
    }

    /** Convenience for callers with no locks. */
    public static Result assign(List<ChestModel> chests, Map<Integer, ItemInfo> items) {
        return assign(chests, items, new HashMap<Integer, String>());
    }
}
