package com.whalestorm.chestsorter.client;

import java.lang.reflect.Method;

import net.minecraft.inventory.Container;

import com.whalestorm.chestsorter.ChestSorter;

import cpw.mods.fml.common.Loader;

/**
 * Optional use of Inventory Bogosorter (GTNH ships it on client and server) to order the slots inside a chest after
 * the run has finished with it. Calls Bogosorter's own client entry point, {@code ClientEventHandler.sort(Container,
 * SlotAccessor, int)}, with no slot (which picks the container's single non-player slot group, i.e. the chest). That
 * sends Bogosorter's sort packet, and its server sorts the open chest by the player's own Bogosorter rules. Read from
 * the source of GTNewHorizons/inventorybogosorter 1.3.54-GTNH (the version in GTNH 2.9.0-RC-2).
 *
 * Found by reflection, so the mod needs no Bogosorter jar to build or run; anything unexpected disables it for the
 * session and the run carries on unsorted.
 */
final class BogoSort {

    private static final String CLASS = "com.cleanroommc.bogosorter.ClientEventHandler";
    private static Method sort;
    private static boolean looked, broken;

    private BogoSort() {}

    static boolean available() {
        if (!looked) {
            looked = true;
            if (!Loader.isModLoaded("bogosorter")) return false;
            try {
                for (Method m : Class.forName(CLASS)
                    .getMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    if (m.getName()
                        .equals("sort") && p.length == 3
                        && p[0] == Container.class
                        && p[2] == int.class
                        && m.getReturnType() == boolean.class) {
                        sort = m;
                        break;
                    }
                }
                if (sort == null) ChestSorter.LOG.warn("Inventory Bogosorter found, but not its sort method");
            } catch (Throwable e) {
                ChestSorter.LOG.warn("Inventory Bogosorter found, but its sort could not be loaded", e);
            }
        }
        return sort != null && !broken;
    }

    /** Asks Bogosorter to sort the open chest. @return true if it sent a sort (false: nothing to sort, or off). */
    static boolean sortOpen(Container container) {
        if (!available()) return false;
        try {
            return (Boolean) sort.invoke(null, container, null, -1);
        } catch (Throwable e) {
            broken = true;
            ChestSorter.LOG.warn("Inventory Bogosorter sort failed; not using it again this session", e);
            return false;
        }
    }
}
