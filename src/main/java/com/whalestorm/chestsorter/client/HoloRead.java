package com.whalestorm.chestsorter.client;

import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.whalestorm.chestsorter.ChestSorter;

import cpw.mods.fml.common.Loader;

/**
 * Reads HoloInventory's client cache of block contents (HoloInventory 2.5.17-GTNH, checked in its source):
 * {@code net.dries007.holoInventory.client.Renderer.tileInventoryMap} maps {@code Coord} (public int dim; double x,
 * y, z) to {@code NamedData} (public ItemStack[] data). Its server fills it for the block a player looks at. Reached
 * by reflection, so this mod neither needs HoloInventory to build nor breaks without it: any mismatch turns this off
 * and the run opens chests instead.
 */
public final class HoloRead {

    private static boolean tried, ok;
    private static Map<?, ?> map;
    private static Field fDim, fX, fY, fZ, fData;

    private HoloRead() {}

    public static synchronized boolean available() {
        if (tried) return ok;
        tried = true;
        try {
            if (!Loader.isModLoaded("holoinventory")) return false;
            Class<?> renderer = Class.forName("net.dries007.holoInventory.client.Renderer");
            map = (Map<?, ?>) renderer.getField("tileInventoryMap")
                .get(null);
            Class<?> coord = Class.forName("net.dries007.holoInventory.util.Coord");
            fDim = coord.getField("dim");
            fX = coord.getField("x");
            fY = coord.getField("y");
            fZ = coord.getField("z");
            fData = Class.forName("net.dries007.holoInventory.util.NamedData")
                .getField("data");
            ok = map != null;
        } catch (Throwable t) {
            ChestSorter.LOG.warn("HoloInventory found but its cache could not be read ({}); chests will be opened", t);
            ok = false;
        }
        return ok;
    }

    /** The cached entry for this block (identity changes when new data arrives), or null. Client thread only. */
    public static Object entry(int dim, int x, int y, int z) {
        if (!available()) return null;
        try {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object k = e.getKey();
                if (fDim.getInt(k) == dim && fX.getDouble(k) == x && fY.getDouble(k) == y && fZ.getDouble(k) == z) {
                    return e.getValue();
                }
            }
        } catch (Throwable t) {
            ChestSorter.LOG.warn("Reading HoloInventory's cache failed: {}", t.toString());
            ok = false;
        }
        return null;
    }

    public static ItemStack[] stacks(Object entry) {
        try {
            Object d = fData.get(entry);
            return d instanceof ItemStack[] ? (ItemStack[]) d : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
