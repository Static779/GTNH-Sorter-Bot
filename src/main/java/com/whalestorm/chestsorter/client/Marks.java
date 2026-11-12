package com.whalestorm.chestsorter.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import com.whalestorm.chestsorter.ChestSorter;
import com.whalestorm.chestsorter.core.BlockPos;
import com.whalestorm.chestsorter.core.Container;

/**
 * Chests the player marked as input (give everything away) or output (receive, sorted) for moving storage. Marks are
 * per block, per dimension, per world: a singleplayer save by its folder, a server by its address, so a mark never
 * applies in another world. Saved in .minecraft/chestsorter/marks.txt, one mark per line:
 * {@code world <tab> dim <tab> x y z <tab> INPUT|OUTPUT}.
 */
final class Marks {

    enum Kind {
        INPUT,
        OUTPUT
    }

    static final Marks INSTANCE = new Marks();

    /** world key → "dim x y z" → kind. */
    private final Map<String, Map<String, Kind>> marks = new TreeMap<>();
    private boolean loaded;

    private Marks() {}

    /** "sp:<save folder>" or "mp:<server address>", or null if not in a world that can be told apart. */
    static String worldKey() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) return null;
        if (mc.isSingleplayer() && mc.getIntegratedServer() != null) {
            String f = mc.getIntegratedServer()
                .getFolderName();
            return f == null || f.isEmpty() ? null : "sp:" + f;
        }
        ServerData sd = mc.func_147104_D(); // getCurrentServerData in later mappings
        if (sd != null && sd.serverIP != null && !sd.serverIP.isEmpty()) return "mp:" + sd.serverIP;
        return null;
    }

    private static String spot(int dim, BlockPos p) {
        return dim + " " + p.x + " " + p.y + " " + p.z;
    }

    /** The blocks a container occupies (both halves of a double chest). */
    static List<BlockPos> blocksOf(Container c) {
        List<BlockPos> out = new ArrayList<>();
        out.add(new BlockPos(c.x, c.y, c.z));
        if (c.partner != null) out.add(c.partner);
        return out;
    }

    /**
     * The container's mark: the same kind on every marked block. {@code conflict[0]} is set when its blocks carry
     * different kinds.
     */
    synchronized Kind kindOf(String world, int dim, Container c, boolean[] conflict) {
        load();
        Map<String, Kind> m = marks.get(world);
        if (m == null) return null;
        Kind found = null;
        for (BlockPos b : blocksOf(c)) {
            Kind k = m.get(spot(dim, b));
            if (k == null) continue;
            if (found != null && found != k && conflict != null) conflict[0] = true;
            found = k;
        }
        return found;
    }

    /** Sets (or with null clears) the mark on every block of these containers, and saves. */
    synchronized void set(String world, int dim, Collection<Container> cs, Kind kind) {
        load();
        Map<String, Kind> m = marks.computeIfAbsent(world, k -> new TreeMap<>());
        for (Container c : cs) {
            for (BlockPos b : blocksOf(c)) {
                if (kind == null) m.remove(spot(dim, b));
                else m.put(spot(dim, b), kind);
            }
        }
        if (m.isEmpty()) marks.remove(world);
        save();
    }

    /** Marked blocks in this world and dimension. */
    synchronized Map<BlockPos, Kind> here(String world, int dim) {
        load();
        Map<BlockPos, Kind> out = new LinkedHashMap<>();
        Map<String, Kind> m = marks.get(world);
        if (m == null) return out;
        String prefix = dim + " ";
        for (Map.Entry<String, Kind> e : m.entrySet()) {
            if (!e.getKey()
                .startsWith(prefix)) continue;
            String[] p = e.getKey()
                .split(" ");
            out.put(new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])), e.getValue());
        }
        return out;
    }

    /** Clears every mark in this world and dimension. @return how many blocks were cleared. */
    synchronized int clear(String world, int dim) {
        load();
        Map<String, Kind> m = marks.get(world);
        if (m == null) return 0;
        int n = 0;
        String prefix = dim + " ";
        for (String k : new ArrayList<>(m.keySet())) {
            if (k.startsWith(prefix)) {
                m.remove(k);
                n++;
            }
        }
        if (m.isEmpty()) marks.remove(world);
        save();
        return n;
    }

    private static File file() {
        return new File(new File(Minecraft.getMinecraft().mcDataDir, "chestsorter"), "marks.txt");
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        File f = file();
        if (!f.isFile()) return;
        try (BufferedReader r = new BufferedReader(
            new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length != 4) continue;
                try {
                    Kind k = Kind.valueOf(p[3].trim());
                    String[] xyz = p[2].trim()
                        .split(" ");
                    if (xyz.length != 3) continue;
                    BlockPos b = new BlockPos(
                        Integer.parseInt(xyz[0]),
                        Integer.parseInt(xyz[1]),
                        Integer.parseInt(xyz[2]));
                    marks.computeIfAbsent(p[0], x -> new TreeMap<>())
                        .put(spot(Integer.parseInt(p[1].trim()), b), k);
                } catch (IllegalArgumentException e) {
                    // skip a damaged line
                }
            }
        } catch (IOException e) {
            ChestSorter.LOG.warn("Could not read {}: {}", f, e.toString());
        }
    }

    private void save() {
        File f = file();
        File dir = f.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            ChestSorter.LOG.warn("Could not create {}", dir);
            return;
        }
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            for (Map.Entry<String, Map<String, Kind>> e : marks.entrySet()) {
                for (Map.Entry<String, Kind> s : e.getValue()
                    .entrySet()) {
                    String[] p = s.getKey()
                        .split(" ", 2);
                    w.println(e.getKey() + "\t" + p[0] + "\t" + p[1] + "\t" + s.getValue());
                }
            }
        } catch (IOException e) {
            ChestSorter.LOG.warn("Could not save {}: {}", f, e.toString());
        }
    }
}
