package com.whalestorm.chestsorter.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.whalestorm.chestsorter.core.ChestModel;

/**
 * Reads an open container. A slot belongs to the player when its inventory is the player's own inventory object;
 * every other slot belongs to the chest. This works for vanilla chests, double chests and every Iron Chest tier
 * without knowing their slot layout.
 */
public final class GuiOps {

    /** First and last index of the player's main inventory (the 27 slots above the hotbar). */
    public static final int MAIN_FIRST = 9, MAIN_LAST = 35;

    private GuiOps() {}

    @SuppressWarnings("unchecked")
    public static List<Slot> slots(Container c) {
        return (List<Slot>) (List<?>) c.inventorySlots;
    }

    public static boolean isPlayerSlot(Slot s, EntityPlayer p) {
        return s.inventory == p.inventory;
    }

    /** Chest slots in container order. */
    public static List<Slot> chestSlots(Container c, EntityPlayer p) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : slots(c)) if (!isPlayerSlot(s, p)) out.add(s);
        return out;
    }

    /** The container slot showing the player's inventory index {@code invIndex}, or null. */
    public static Slot playerSlot(Container c, EntityPlayer p, int invIndex) {
        for (Slot s : slots(c)) if (isPlayerSlot(s, p) && s.getSlotIndex() == invIndex) return s;
        return null;
    }

    /** Snapshot of the chest part as a model (keys from {@code keys}). */
    public static ChestModel readChest(Container c, EntityPlayer p, ItemKeys keys, int id,
        com.whalestorm.chestsorter.core.BlockPos pos, String name) {
        List<Slot> chest = chestSlots(c, p);
        int limit = chest.isEmpty() ? 64
            : chest.get(0)
                .getSlotStackLimit();
        ChestModel m = new ChestModel(id, pos, name, chest.size(), limit);
        for (int i = 0; i < chest.size(); i++) {
            ItemStack st = chest.get(i)
                .getStack();
            m.set(i, keys.keyOf(st), st == null ? 0 : st.stackSize);
        }
        return m;
    }

    /**
     * Item totals over every slot of the container plus the cursor. A transfer inside the container keeps this equal.
     */
    public static Map<Integer, Long> totals(Container c, EntityPlayer p, ItemKeys keys) {
        Map<Integer, Long> t = new TreeMap<>();
        for (Slot s : slots(c)) add(t, keys, s.getStack());
        add(t, keys, p.inventory.getItemStack());
        return t;
    }

    private static void add(Map<Integer, Long> t, ItemKeys keys, ItemStack st) {
        int k = keys.keyOf(st);
        if (k != ChestModel.EMPTY) t.merge(k, (long) st.stackSize, Long::sum);
    }

    public static String describe(ItemStack st, ItemKeys keys) {
        if (st == null) return "empty";
        return st.stackSize + " x " + keys.label(keys.keyOf(st));
    }

    /** Readable diff of two totals maps, for error messages. */
    public static String diff(Map<Integer, Long> before, Map<Integer, Long> after, ItemKeys keys) {
        StringBuilder sb = new StringBuilder();
        java.util.Set<Integer> all = new java.util.TreeSet<>(before.keySet());
        all.addAll(after.keySet());
        for (int k : all) {
            long b = before.containsKey(k) ? before.get(k) : 0, a = after.containsKey(k) ? after.get(k) : 0;
            if (a != b) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(keys.label(k))
                    .append(' ')
                    .append(b)
                    .append(" -> ")
                    .append(a);
            }
        }
        return sb.length() == 0 ? "(no difference)" : sb.toString();
    }
}
