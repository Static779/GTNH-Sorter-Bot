package com.whalestorm.chestsorter.client;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.whalestorm.chestsorter.ChestSorter;
import com.whalestorm.chestsorter.core.Categorizer;
import com.whalestorm.chestsorter.core.ChestModel;
import com.whalestorm.chestsorter.core.ItemInfo;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Hands out an int key per distinct item. Two stacks share a key only if they have the same Item, the same damage
 * value and equal NBT (vanilla's ItemStack.areItemStackTagsEqual). That is at least as strict as the rule vanilla uses
 * to merge stacks, so stacks with one key always merge, and the totals checks compare like with like.
 *
 * Each key also has a loose key: same Item, and same damage only for items with subtypes; NBT ignored. Safety checks
 * compare loose keys, because some items change their own NBT or durability while sitting in the inventory (a
 * Tinkers' Construct tool did, which stopped a run with "Alumite Hammer 1 -> 0, Alumite Hammer 0 -> 1"). Counts per
 * slot are still checked exactly. A loose key is the first strict key seen with that identity, so labels work.
 *
 * One instance per run; keys are not stable between runs.
 */
public final class ItemKeys {

    private static List<String> gtPrefixes;

    private final Categorizer categorizer = new Categorizer(gtPrefixes());
    /** Prototype copy of each key's stack (stack size 1). Index = key. */
    private final List<ItemStack> prototypes = new ArrayList<>();
    private final Map<Item, List<Integer>> byItem = new IdentityHashMap<>();
    private final Map<Integer, ItemInfo> infos = new HashMap<>();
    private final Map<Integer, List<String>> oreNames = new HashMap<>();
    /** strict key → loose key. */
    private final List<Integer> looseOfKey = new ArrayList<>();
    private final Map<Item, Map<Integer, Integer>> looseByItem = new IdentityHashMap<>();

    /** @return the key for this stack, or {@link ChestModel#EMPTY} for null / empty stacks. */
    public int keyOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return ChestModel.EMPTY;
        List<Integer> candidates = byItem.get(stack.getItem());
        if (candidates != null) {
            for (int k : candidates) {
                if (same(prototypes.get(k), stack)) return k;
            }
        } else {
            candidates = new ArrayList<>(2);
            byItem.put(stack.getItem(), candidates);
        }
        int key = prototypes.size();
        ItemStack proto = stack.copy();
        proto.stackSize = 1;
        prototypes.add(proto);
        candidates.add(key);
        int meta = hasSubtypes(stack) ? stack.getItemDamage() : 0;
        Map<Integer, Integer> metas = looseByItem.get(stack.getItem());
        if (metas == null) {
            metas = new HashMap<>();
            looseByItem.put(stack.getItem(), metas);
        }
        Integer loose = metas.get(meta);
        if (loose == null) {
            loose = key;
            metas.put(meta, key);
        }
        looseOfKey.add(loose);
        List<String> names = oreNamesOf(stack);
        oreNames.put(key, names);
        infos.put(
            key,
            new ItemInfo(
                key,
                label(stack),
                categorizer.categorize(names, modIdOf(stack), tabOf(stack)),
                stack.getMaxStackSize()));
        return key;
    }

    /** Loose key for a strict key ({@link ChestModel#EMPTY} stays EMPTY). */
    public int loose(int key) {
        if (key < 0 || key >= looseOfKey.size()) return key;
        return looseOfKey.get(key);
    }

    /** Loose key of a stack. */
    public int looseOf(ItemStack stack) {
        return loose(keyOf(stack));
    }

    private static boolean hasSubtypes(ItemStack stack) {
        try {
            return stack.getHasSubtypes();
        } catch (Throwable t) {
            return true; // unknown: keep damage in the key (stricter)
        }
    }

    public static boolean same(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage()
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    public Map<Integer, ItemInfo> infos() {
        return Collections.unmodifiableMap(infos);
    }

    public ItemInfo info(int key) {
        return infos.get(key);
    }

    public String label(int key) {
        ItemInfo i = infos.get(key);
        return i == null ? ("key " + key) : i.label;
    }

    public List<String> oreNames(int key) {
        List<String> n = oreNames.get(key);
        return n == null ? Collections.<String>emptyList() : n;
    }

    // ---- item facts, each guarded: a broken item from any mod must not stop a run ----

    static String label(ItemStack stack) {
        try {
            return stack.getDisplayName();
        } catch (Throwable t) {
            return String.valueOf(stack.getItem());
        }
    }

    static List<String> oreNamesOf(ItemStack stack) {
        List<String> names = new ArrayList<>();
        try {
            for (int id : OreDictionary.getOreIDs(stack)) {
                String n = OreDictionary.getOreName(id);
                if (n != null && !"Unknown".equals(n)) names.add(n);
            }
        } catch (Throwable t) {
            ChestSorter.LOG.warn("Ore dictionary lookup failed for {}: {}", label(stack), t.toString());
        }
        Collections.sort(names);
        return names;
    }

    /** Creative tab label (e.g. "redstone", "GregTech.Main"), or null. Used to split big mod buckets. */
    static String tabOf(ItemStack stack) {
        try {
            net.minecraft.creativetab.CreativeTabs tab = stack.getItem()
                .getCreativeTab();
            return tab == null ? null : tab.getTabLabel();
        } catch (Throwable t) {
            return null;
        }
    }

    static String modIdOf(ItemStack stack) {
        try {
            GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(stack.getItem());
            return id == null ? null : id.modId;
        } catch (Throwable t) {
            return null;
        }
    }

    /** GT ore prefixes bundled in the jar (assets/chestsorter/gt-ore-prefixes.txt). */
    static synchronized List<String> gtPrefixes() {
        if (gtPrefixes != null) return gtPrefixes;
        List<String> out = new ArrayList<>();
        try (InputStream in = ItemKeys.class.getResourceAsStream("/assets/chestsorter/gt-ore-prefixes.txt")) {
            if (in == null) {
                ChestSorter.LOG.warn("gt-ore-prefixes.txt missing from the jar; using lowercase-run prefixes only");
            } else {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) out.add(line);
            }
        } catch (Exception e) {
            ChestSorter.LOG.warn("Could not read gt-ore-prefixes.txt: {}", e.toString());
        }
        gtPrefixes = out;
        return out;
    }
}
