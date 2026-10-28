package com.whalestorm.chestsorter.core;

/**
 * One sortable container found by the scanner. A vanilla double chest is a single Container whose
 * {@link #partner} points at the other half. Immutable.
 */
public final class Container {

    public final int x, y, z;
    /** Player-facing name, e.g. "Chest", "Double Chest", "Diamond Chest". */
    public final String name;
    public final int slots;
    /** Squared distance from the player to this block's centre. */
    public final double distanceSq;
    /** Other half of a double chest, or null. */
    public final BlockPos partner;

    public Container(int x, int y, int z, String name, int slots, double distanceSq, BlockPos partner) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.name = name;
        this.slots = slots;
        this.distanceSq = distanceSq;
        this.partner = partner;
    }

    public double distance() {
        return Math.sqrt(distanceSq);
    }

    @Override
    public String toString() {
        String where = partner == null ? x + " " + y + " " + z
            : x + " " + y + " " + z + " + " + partner.x + " " + partner.y + " " + partner.z;
        return name + " ("
            + slots
            + " slots) at "
            + where
            + String.format(java.util.Locale.ROOT, ", %.1f m", distance());
    }
}
