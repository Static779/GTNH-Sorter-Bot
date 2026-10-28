package com.whalestorm.chestsorter.core;

/** A block position. 1.7.10 has no BlockPos class, so this is our own. Immutable value type. */
public final class BlockPos implements Comparable<BlockPos> {

    public final int x, y, z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** True if the two positions share a face horizontally (same y, one step in x or z). */
    public boolean isHorizontalNeighbour(BlockPos o) {
        return y == o.y && Math.abs(x - o.x) + Math.abs(z - o.z) == 1;
    }

    @Override
    public int compareTo(BlockPos o) {
        if (y != o.y) return Integer.compare(y, o.y);
        if (x != o.x) return Integer.compare(x, o.x);
        return Integer.compare(z, o.z);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BlockPos)) return false;
        BlockPos p = (BlockPos) o;
        return x == p.x && y == p.y && z == p.z;
    }

    @Override
    public int hashCode() {
        return (x * 31 + y) * 31 + z;
    }

    @Override
    public String toString() {
        return x + " " + y + " " + z;
    }
}
