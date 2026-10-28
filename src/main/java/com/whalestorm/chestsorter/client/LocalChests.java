package com.whalestorm.chestsorter.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.whalestorm.chestsorter.core.Container;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Singleplayer only: reads chest contents straight from the game's built-in server, which runs in the same process.
 * No walking, no opening, exact slots and counts.
 *
 * The read happens on the server thread (in its tick event), so the server is never read while it is changing a
 * chest; the client thread only sees finished copies. A double chest is read lower half first, the same order its
 * chest screen shows (vanilla puts the half with the lower x or z first). Anything unexpected for a chest (missing,
 * not an inventory, slot count differs from the scan) leaves that chest out, and the run opens it instead.
 */
public final class LocalChests {

    public static final LocalChests INSTANCE = new LocalChests();

    public static final class Request {

        final int dimension;
        final List<Container> containers;
        /** Container index → copied slot contents. Set before {@link #done}. */
        volatile Map<Integer, ItemStack[]> result;
        volatile String error;
        volatile boolean done;

        Request(int dimension, List<Container> containers) {
            this.dimension = dimension;
            this.containers = new ArrayList<>(containers);
        }
    }

    private volatile Request pending;

    private LocalChests() {}

    /** True when this client is running its own singleplayer (or LAN-host) world. */
    public static boolean available() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.isSingleplayer() && mc.getIntegratedServer() != null;
    }

    public Request request(int dimension, List<Container> containers) {
        Request r = new Request(dimension, containers);
        pending = r;
        return r;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Request r = pending;
        if (r == null) return;
        pending = null;
        try {
            r.result = read(r);
        } catch (Throwable t) {
            r.error = t.toString();
        } finally {
            r.done = true;
        }
    }

    private static Map<Integer, ItemStack[]> read(Request r) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) throw new IllegalStateException("no singleplayer server");
        WorldServer world = server.worldServerForDimension(r.dimension);
        if (world == null) throw new IllegalStateException("dimension " + r.dimension + " not loaded on the server");
        Map<Integer, ItemStack[]> out = new HashMap<>();
        for (int i = 0; i < r.containers.size(); i++) {
            Container c = r.containers.get(i);
            List<IInventory> parts = new ArrayList<>();
            IInventory a = inventoryAt(world, c.x, c.y, c.z);
            if (a == null) continue;
            parts.add(a);
            if (c.partner != null) {
                IInventory b = inventoryAt(world, c.partner.x, c.partner.y, c.partner.z);
                if (b == null) continue;
                // The scanner lists a double chest at its lower half, so this is lower half first.
                parts.add(b);
            }
            int size = 0;
            for (IInventory inv : parts) size += inv.getSizeInventory();
            if (size != c.slots) continue;
            ItemStack[] slots = new ItemStack[size];
            int k = 0;
            for (IInventory inv : parts) {
                for (int s = 0; s < inv.getSizeInventory(); s++) {
                    ItemStack st = inv.getStackInSlot(s);
                    slots[k++] = st == null ? null : st.copy();
                }
            }
            out.put(i, slots);
        }
        return out;
    }

    private static IInventory inventoryAt(WorldServer world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof IInventory ? (IInventory) te : null;
    }
}
