package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Replays a plan on copies of the surveyed chests and lists every rule it breaks. The client refuses to execute a
 * plan with any problem. Checks:
 * 1. Every pick takes exactly what its slot holds at that moment (key and count), once.
 * 2. Every picked stack is put in exactly once, later in the same trip, into a chest of its category, never the
 * chest it came from.
 * 3. Every deposit fits completely.
 * 4. No more stacks are carried at once than there are carry slots, and nothing is carried at the end of a trip.
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
            int[] state = new int[trip.picks.size()]; // 0 not taken, 1 carried, 2 put in
            int carried = 0;
            for (Plan.Visit v : trip.visits) {
                ChestModel c = sim.get(v.chest);
                if (c == null) {
                    problems.add(where + ": unknown chest " + v.chest);
                    continue;
                }
                for (Plan.Op op : v.ops) {
                    if (op.pick < 0 || op.pick >= trip.picks.size()) {
                        problems.add(where + ": op refers to pick " + op.pick);
                        continue;
                    }
                    Plan.Pick p = trip.picks.get(op.pick);
                    if (!op.deposit) {
                        if (state[op.pick] != 0) problems.add(where + ": pick " + op.pick + " taken twice");
                        if (p.chest != v.chest) problems.add(where + ": pick " + op.pick + " taken at the wrong chest");
                        if (p.slot < 0 || p.slot >= c.size()) {
                            problems.add(where + ": slot " + p.slot + " out of range");
                            continue;
                        }
                        if (c.keys[p.slot] != p.key || c.counts[p.slot] != p.count) {
                            problems.add(
                                where + ": #"
                                    + v.chest
                                    + " slot "
                                    + p.slot
                                    + " holds "
                                    + c.counts[p.slot]
                                    + " x key "
                                    + c.keys[p.slot]
                                    + ", plan takes "
                                    + p.count
                                    + " x key "
                                    + p.key);
                        }
                        c.set(p.slot, ChestModel.EMPTY, 0);
                        state[op.pick] = 1;
                        if (++carried > carrySlots) {
                            problems.add(where + ": carries " + carried + " stacks but has " + carrySlots + " slots");
                        }
                    } else {
                        if (state[op.pick] != 1) {
                            problems.add(where + ": pick " + op.pick + " put in while not carried");
                            continue;
                        }
                        if (p.chest == v.chest)
                            problems.add(where + ": pick " + op.pick + " put back where it came from");
                        ItemInfo info = items.get(p.key);
                        if (info == null) {
                            problems.add(where + ": no item info for key " + p.key);
                            continue;
                        }
                        if (!plan.effective(info.category)
                            .equals(plan.chestCategory.get(v.chest))) {
                            problems.add(
                                where + ": "
                                    + info.label
                                    + " ["
                                    + info.category
                                    + "] sent to chest "
                                    + v.chest
                                    + " ["
                                    + plan.chestCategory.get(v.chest)
                                    + "]");
                        }
                        int left = c.place(p.key, p.count, info.maxStack);
                        if (left != 0)
                            problems.add(where + ": " + left + " x " + info.label + " do not fit in chest " + v.chest);
                        state[op.pick] = 2;
                        carried--;
                    }
                }
            }
            for (int i = 0; i < state.length; i++) {
                if (state[i] != 2)
                    problems.add(where + ": pick " + i + (state[i] == 0 ? " never taken" : " never put in"));
            }
        }

        Map<Integer, Long> totalsAfter = totals(sim.values());
        if (!totalsBefore.equals(totalsAfter)) {
            problems.add("item totals change: before " + totalsBefore + ", after " + totalsAfter);
        }
        for (ChestModel c : sim.values()) {
            ChestModel exp = plan.expected.get(c.id);
            if (exp == null || !java.util.Arrays.equals(exp.keys, c.keys)
                || !java.util.Arrays.equals(exp.counts, c.counts)) {
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
