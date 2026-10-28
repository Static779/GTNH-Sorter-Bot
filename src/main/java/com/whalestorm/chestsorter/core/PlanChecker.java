package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Replays a plan on copies of the surveyed chests and lists every rule it breaks. The client refuses to execute a
 * plan with any problem. Checks:
 * 1. Every pick takes exactly what the source slot holds (key and count) and each slot is picked at most once.
 * 2. Every picked stack is deposited exactly once, into a chest whose category is the stack's category.
 * 3. Every deposit fits completely.
 * 4. Trip size is within the carry limit.
 * 5. Item totals across all chests are identical before and after (nothing created or lost).
 * 6. The replay ends in exactly the state the plan says it will.
 */
public final class PlanChecker {

    private PlanChecker() {}

    public static List<String> check(List<ChestModel> before, Plan plan, Map<Integer, ItemInfo> items, int carrySlots) {
        List<String> problems = new ArrayList<>();
        Map<Integer, ChestModel> sim = new LinkedHashMap<>();
        for (ChestModel c : before) sim.put(c.id, c.copy());
        Map<Integer, Long> totalsBefore = totals(sim.values());

        int tripNo = 0;
        for (Plan.Trip trip : plan.trips) {
            tripNo++;
            String where = "trip " + tripNo;
            ChestModel src = sim.get(trip.source);
            if (src == null) {
                problems.add(where + ": unknown source chest " + trip.source);
                continue;
            }
            if (trip.picks.size() > carrySlots) {
                problems.add(where + ": " + trip.picks.size() + " stacks but only " + carrySlots + " carry slots");
            }
            boolean[] picked = new boolean[src.size()];
            for (Plan.Pick p : trip.picks) {
                if (p.slot < 0 || p.slot >= src.size()) {
                    problems.add(where + ": slot " + p.slot + " out of range");
                    continue;
                }
                if (picked[p.slot]) problems.add(where + ": slot " + p.slot + " picked twice");
                picked[p.slot] = true;
                if (src.keys[p.slot] != p.key || src.counts[p.slot] != p.count) {
                    problems.add(where + ": slot " + p.slot + " holds " + src.counts[p.slot] + " x key " + src.keys[p.slot]
                        + ", plan picks " + p.count + " x key " + p.key);
                }
                src.set(p.slot, ChestModel.EMPTY, 0);
            }
            int[] depositedTimes = new int[trip.picks.size()];
            for (Plan.Deposit d : trip.deposits) {
                ChestModel tgt = sim.get(d.target);
                if (tgt == null) {
                    problems.add(where + ": unknown target chest " + d.target);
                    continue;
                }
                if (d.target == trip.source) problems.add(where + ": deposits back into its own source");
                for (int idx : d.picks) {
                    if (idx < 0 || idx >= trip.picks.size()) {
                        problems.add(where + ": deposit refers to pick " + idx);
                        continue;
                    }
                    depositedTimes[idx]++;
                    Plan.Pick p = trip.picks.get(idx);
                    ItemInfo info = items.get(p.key);
                    if (info == null) {
                        problems.add(where + ": no item info for key " + p.key);
                        continue;
                    }
                    if (!info.category.equals(plan.chestCategory.get(d.target))) {
                        problems.add(where + ": " + info.label + " [" + info.category + "] sent to chest " + d.target
                            + " [" + plan.chestCategory.get(d.target) + "]");
                    }
                    int left = tgt.place(p.key, p.count, info.maxStack);
                    if (left != 0) problems.add(where + ": " + left + " x " + info.label + " do not fit in chest " + d.target);
                }
            }
            for (int i = 0; i < depositedTimes.length; i++) {
                if (depositedTimes[i] != 1) {
                    problems.add(where + ": pick " + i + " deposited " + depositedTimes[i] + " times");
                }
            }
        }

        Map<Integer, Long> totalsAfter = totals(sim.values());
        if (!totalsBefore.equals(totalsAfter)) {
            problems.add("item totals change: before " + totalsBefore + ", after " + totalsAfter);
        }
        for (ChestModel c : sim.values()) {
            ChestModel exp = plan.expected.get(c.id);
            if (exp == null || !java.util.Arrays.equals(exp.keys, c.keys) || !java.util.Arrays.equals(exp.counts, c.counts)) {
                problems.add("chest " + c.id + ": replay result differs from the plan's expected state");
            }
        }
        return problems;
    }

    public static Map<Integer, Long> totals(Iterable<ChestModel> chests) {
        Map<Integer, Long> t = new TreeMap<>();
        for (ChestModel c : chests) {
            for (Map.Entry<Integer, Long> e : c.totals()
                .entrySet()) t.merge(e.getKey(), e.getValue(), Long::sum);
        }
        return t;
    }
}
