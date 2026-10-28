package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Output of {@link Planner}: one route through the chests, cut into trips. A trip starts and ends with nothing
 * carried; in between it visits chests in order, and at each visit takes stacks out and puts carried stacks in, in
 * the order given (putting in can come after taking out, so a full chest can swap with another). Immutable.
 */
public final class Plan {

    /** Take one whole stack out of a slot of a chest. */
    public static final class Pick {

        public final int chest, slot, key, count;

        public Pick(int chest, int slot, int key, int count) {
            this.chest = chest;
            this.slot = slot;
            this.key = key;
            this.count = count;
        }

        @Override
        public String toString() {
            return "#" + chest + " slot " + slot + ": " + count + " x key " + key;
        }
    }

    /** One action at a visit: take pick {@code pick} out of this chest, or put carried pick {@code pick} in. */
    public static final class Op {

        public final boolean deposit;
        /** Index into {@link Trip#picks}. */
        public final int pick;

        public Op(boolean deposit, int pick) {
            this.deposit = deposit;
            this.pick = pick;
        }

        @Override
        public String toString() {
            return (deposit ? "put " : "take ") + pick;
        }
    }

    /** Open one chest and do its ops in order. */
    public static final class Visit {

        public final int chest;
        public final List<Op> ops;

        public Visit(int chest, List<Op> ops) {
            this.chest = chest;
            this.ops = Collections.unmodifiableList(new ArrayList<>(ops));
        }

        public int picks() {
            int n = 0;
            for (Op o : ops) if (!o.deposit) n++;
            return n;
        }

        public int deposits() {
            return ops.size() - picks();
        }
    }

    public static final class Trip {

        public final List<Pick> picks;
        public final List<Visit> visits;

        public Trip(List<Pick> picks, List<Visit> visits) {
            this.picks = Collections.unmodifiableList(new ArrayList<>(picks));
            this.visits = Collections.unmodifiableList(new ArrayList<>(visits));
        }

        /** The first chest visited. */
        public int source() {
            return visits.isEmpty() ? -1 : visits.get(0).chest;
        }

        /** The last chest visited. */
        public int end() {
            return visits.isEmpty() ? -1 : visits.get(visits.size() - 1).chest;
        }

        /** Most stacks carried at once on this trip. */
        public int maxCarried() {
            int now = 0, max = 0;
            for (Visit v : visits) {
                for (Op o : v.ops) {
                    now += o.deposit ? -1 : 1;
                    max = Math.max(max, now);
                }
            }
            return max;
        }

        public int itemCount() {
            int n = 0;
            for (Pick p : picks) n += p.count;
            return n;
        }

        /** The chest pick {@code pick} is put into, or -1. */
        public int targetOf(int pick) {
            for (Visit v : visits) for (Op o : v.ops) if (o.deposit && o.pick == pick) return v.chest;
            return -1;
        }
    }

    public final List<Trip> trips;
    /** chest id → category, from the Assigner. */
    public final Map<Integer, String> chestCategory;
    /** Categories with no chest; their items are left in place. */
    public final List<String> unassigned;
    /** Small category → the category whose chests take its items. */
    public final Map<String, String> merged;
    /** Misplaced stacks that had nowhere to go (target chests full). Left in place. */
    public final int stacksLeftNoRoom;
    /** Expected end state per chest id if every trip succeeds. */
    public final Map<Integer, ChestModel> expected;

    public Plan(List<Trip> trips, Map<Integer, String> chestCategory, List<String> unassigned,
        Map<String, String> merged, int stacksLeftNoRoom, Map<Integer, ChestModel> expected) {
        this.trips = Collections.unmodifiableList(new ArrayList<>(trips));
        this.chestCategory = Collections.unmodifiableMap(chestCategory);
        this.unassigned = Collections.unmodifiableList(new ArrayList<>(unassigned));
        this.merged = Collections.unmodifiableMap(merged);
        this.stacksLeftNoRoom = stacksLeftNoRoom;
        this.expected = Collections.unmodifiableMap(expected);
    }

    /** The category whose chests an item of {@code category} belongs in. */
    public String effective(String category) {
        String m = merged.get(category);
        return m != null ? m : category;
    }

    public int stackMoves() {
        int n = 0;
        for (Trip t : trips) n += t.picks.size();
        return n;
    }

    public int visits() {
        int n = 0;
        for (Trip t : trips) n += t.visits.size();
        return n;
    }

    public boolean isEmpty() {
        return trips.isEmpty();
    }
}
