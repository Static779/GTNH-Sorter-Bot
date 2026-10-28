package com.whalestorm.chestsorter.client;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.whalestorm.chestsorter.core.PathFinder;

/**
 * Pathfinding view of the client world. Unknown or odd blocks count as neither open nor floor, so the walker routes
 * around anything it cannot judge instead of guessing.
 */
public final class ClientTerrain implements PathFinder.Terrain {

    private final World world;

    public ClientTerrain(World world) {
        this.world = world;
    }

    @Override
    public boolean open(int x, int y, int z) {
        if (y < 0 || y > 255 || !world.blockExists(x, y, z)) return false;
        try {
            Block b = world.getBlock(x, y, z);
            if (harmful(b.getMaterial())) return false;
            return b.getCollisionBoundingBoxFromPool(world, x, y, z) == null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean floor(int x, int y, int z) {
        if (y < 0 || y > 255 || !world.blockExists(x, y, z)) return false;
        try {
            Block b = world.getBlock(x, y, z);
            if (harmful(b.getMaterial())) return false;
            AxisAlignedBB box = b.getCollisionBoundingBoxFromPool(world, x, y, z);
            if (box == null) return false;
            double top = box.maxY - y;
            // Slabs (0.5) up to full blocks (1.0). Fences and walls (1.5) are obstacles, not floors.
            return top >= 0.5 && top <= 1.0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean harmful(Material m) {
        return m.isLiquid() || m == Material.lava
            || m == Material.fire
            || m == Material.cactus
            || m == Material.web
            || m == Material.portal;
    }
}
