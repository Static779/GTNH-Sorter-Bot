package com.whalestorm.chestsorter.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;

import com.whalestorm.chestsorter.ChestSorter;
import com.whalestorm.chestsorter.core.BlockPos;
import com.whalestorm.chestsorter.core.ChestPairing;
import com.whalestorm.chestsorter.core.Container;

/**
 * Stage 1: finds sortable containers near the player from the client's own copy of the world. Reads positions and
 * slot counts only; contents are not synced to the client until a chest is opened.
 *
 * Allow-list (your choice, 2026-10-27): vanilla chests (incl. trapped) and Iron Chests, except the 1-slot Dirt Chest.
 * GT machines also implement IInventory, which is why this is an allow-list and not "anything with an inventory".
 */
public final class ContainerScanner {

    /** Base class of all 11 Iron Chest tiers. Looked up by name so the mod still loads without Iron Chests. */
    private static final Class<?> IRON_CHEST = classOrNull("cpw.mods.ironchest.TileEntityIronChest");
    private static final Class<?> DIRT_CHEST = classOrNull("cpw.mods.ironchest.TileEntityDirtChest");

    private ContainerScanner() {}

    /** @return containers within {@code radius} blocks of the player, nearest first. */
    public static List<Container> scan(World world, EntityClientPlayerMP player, int radius) {
        double radiusSq = (double) radius * radius;
        Map<BlockPos, TileEntityChest> vanilla = new HashMap<>();
        List<Container> found = new ArrayList<>();

        // Copy first: the world can add or remove tile entities while we iterate.
        for (Object o : new ArrayList<Object>(world.loadedTileEntityList)) {
            if (!(o instanceof TileEntity) || !(o instanceof IInventory)) continue;
            TileEntity te = (TileEntity) o;
            if (te.isInvalid()) continue;
            double distSq = distanceSq(te, player);
            if (distSq > radiusSq) continue;

            if (te instanceof TileEntityChest) {
                vanilla.put(new BlockPos(te.xCoord, te.yCoord, te.zCoord), (TileEntityChest) te);
            } else if (isIronChest(te)) {
                IInventory inv = (IInventory) te;
                found.add(
                    new Container(
                        te.xCoord,
                        te.yCoord,
                        te.zCoord,
                        inv.getInventoryName(),
                        inv.getSizeInventory(),
                        distSq,
                        null));
            }
        }

        // Join double chests. A chest's block (chest vs trapped chest) decides which halves may join.
        Map<BlockPos, Object> blockKeys = new HashMap<>();
        for (Map.Entry<BlockPos, TileEntityChest> e : vanilla.entrySet()) {
            BlockPos p = e.getKey();
            blockKeys.put(p, world.getBlock(p.x, p.y, p.z));
        }
        Map<BlockPos, BlockPos> partners = ChestPairing.pair(blockKeys);
        for (Map.Entry<BlockPos, TileEntityChest> e : vanilla.entrySet()) {
            BlockPos p = e.getKey();
            BlockPos partner = partners.get(p);
            if (partner != null && partner.compareTo(p) < 0) continue; // list each double chest once
            TileEntityChest chest = e.getValue();
            int slots = chest.getSizeInventory();
            double distSq = distanceSq(chest, player);
            String name = "Chest";
            if (partner != null) {
                TileEntityChest other = vanilla.get(partner);
                slots += other.getSizeInventory();
                distSq = Math.min(distSq, distanceSq(other, player));
                name = "Double Chest";
            }
            found.add(new Container(p.x, p.y, p.z, name, slots, distSq, partner));
        }

        found.sort(Comparator.comparingDouble(c -> c.distanceSq));
        return found;
    }

    private static boolean isIronChest(TileEntity te) {
        return IRON_CHEST != null && IRON_CHEST.isInstance(te) && (DIRT_CHEST == null || !DIRT_CHEST.isInstance(te));
    }

    private static double distanceSq(TileEntity te, EntityClientPlayerMP player) {
        double dx = te.xCoord + 0.5 - player.posX;
        double dy = te.yCoord + 0.5 - player.posY;
        double dz = te.zCoord + 0.5 - player.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            ChestSorter.LOG.info("{} not present; Iron Chests will not be scanned", name);
            return null;
        }
    }
}
