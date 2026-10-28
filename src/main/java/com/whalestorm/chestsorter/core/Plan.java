package com.whalestorm.chestsorter.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Output of {@link Planner}: the trips to make and the state the chests should end in. Immutable after build. */
public final class Plan {

    /** Take one whole stack out of a source slot. */
    public static final class Pick {

        public final int slot, key, count;

        public Pick(int slot, int key, int count) {
            this.slot = slot;
            this.key = key;
            this.count = count;
        }

        @Override
        public String toString() {
            return "slot " + slot + ": " + count + " x key " + key;
        }
    }

    /** Put some of the trip's picked stacks into one target chest. */
    public static final class Deposit {

        public final int target;
        /** Indexes into {@link Trip#picks}, in the order they are placed. */
        public final List<Integer> picks;

        public Deposit(int target, List<Integer> picks) {
            this.target = target;
            this.picks = Collections.unmodifiableList(new ArrayList<>(picks));
        }
    }

    /** One loop: open the source, pick stacks into empty inventory slots, then visit each target in order. */
    public static final class Trip {

        public final int source;
        public final List<Pick> picks;
        public final List<Deposit> deposits;

        public Trip(int source, List<Pick> picks, List<Deposit> deposits) {
            this.source = source;
            this.picks = Collections.unmodifiableList(new ArrayList<>(picks));
            this.deposits = Collections.unmodifiableList(new ArrayList<>(deposits));
        }

        public int itemCount() {
            int n = 0;
            for (Pick p : picks) n += p.count;
            return n;
        }
    }

    public final List<Trip> trips;
    /** chest id → category, from the Assigner. */
    public final Map<Integer, String> chestCategory;
    /** Categories with no chest; their items are left in place. */
    public final List<String> unassigned;
    /** Misplaced stacks that had nowhere to go (target chests full). Left in place. */
    public final int stacksLeftNoRoom;
    /** Expected end state per chest id if every trip succeeds. */
    public final Map<Integer, ChestModel> expected;

    public Plan(List<Trip> trips, Map<Integer, String> chestCategory, List<String> unassigned, int stacksLeftNoRoom,
        Map<Integer, ChestModel> expected) {
        this.trips = Collections.unmodifiableList(new ArrayList<>(trips));
        this.chestCategory = Collections.unmodifiableMap(chestCategory);
        this.unassigned = Collections.unmodifiableList(new ArrayList<>(unassigned));
        this.stacksLeftNoRoom = stacksLeftNoRoom;
        this.expected = Collections.unmodifiableMap(expected);
    }

    public int stackMoves() {
        int n = 0;
        for (Trip t : trips) n += t.picks.size();
        return n;
    }

    public boolean isEmpty() {
        return trips.isEmpty();
    }
}
