#!/usr/bin/env python3
"""
Scan mod jars for TileEntity classes that implement IInventory (directly, via a superclass, or via an
interface that extends it). Reads class-file headers only; never loads or runs mod code.

Usage: python3 -I scan_containers.py <mods_dir> <out_dir>
Writes: containers.csv (one row per inventory TileEntity class), summary.txt (counts per jar).

Limits: Vanilla Minecraft classes are not in the pack, so their hierarchy is seeded from VANILLA below.
A class whose ancestor lives in a jar we don't have (CurseForge-only mods) is reported as 'unknown-parent'.
"""
import csv, os, struct, sys, zipfile
from collections import Counter, defaultdict

TE = "net/minecraft/tileentity/TileEntity"
INV = "net/minecraft/inventory/IInventory"
# Vanilla 1.7.10 seeds: class -> (super, [interfaces]). From Minecraft 1.7.10 as named by MCP.
VANILLA = {
    TE: ("java/lang/Object", []),
    INV: (None, []),
    "net/minecraft/inventory/ISidedInventory": (None, [INV]),
    "net/minecraft/tileentity/IHopper": (None, [INV]),
    "net/minecraft/tileentity/TileEntityChest": (TE, [INV]),
    "net/minecraft/tileentity/TileEntityFurnace": (TE, ["net/minecraft/inventory/ISidedInventory"]),
    "net/minecraft/tileentity/TileEntityDispenser": (TE, [INV]),
    "net/minecraft/tileentity/TileEntityDropper": ("net/minecraft/tileentity/TileEntityDispenser", []),
    "net/minecraft/tileentity/TileEntityHopper": (TE, ["net/minecraft/tileentity/IHopper"]),
    "net/minecraft/tileentity/TileEntityBrewingStand": (TE, ["net/minecraft/inventory/ISidedInventory"]),
    "net/minecraft/tileentity/TileEntityBeacon": (TE, [INV]),
    "net/minecraft/tileentity/TileEntityEnderChest": (TE, []),
}
ACC_INTERFACE, ACC_ABSTRACT = 0x0200, 0x0400


def parse_class(data):
    """Return (name, super, interfaces, access) from a class file, or None if malformed."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        return None
    pos, n = 10, struct.unpack(">H", data[8:10])[0]
    utf8, classes, i = {}, {}, 1
    while i < n:
        tag = data[pos]
        if tag == 1:
            ln = struct.unpack(">H", data[pos + 1:pos + 3])[0]
            utf8[i] = data[pos + 3:pos + 3 + ln].decode("utf-8", "replace")
            pos += 3 + ln
        elif tag == 7:
            classes[i] = struct.unpack(">H", data[pos + 1:pos + 3])[0]
            pos += 3
        elif tag in (8, 16, 19, 20):
            pos += 3
        elif tag == 15:
            pos += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            i += 1  # long/double take two slots
        else:
            return None
        i += 1
    access, this_i, super_i, ic = struct.unpack(">HHHH", data[pos:pos + 8])
    pos += 8
    ifaces = [utf8[classes[struct.unpack(">H", data[pos + 2 * k:pos + 2 * k + 2])[0]]] for k in range(ic)]
    sup = utf8[classes[super_i]] if super_i else None
    return utf8[classes[this_i]], sup, ifaces, access


def main(mods_dir, out_dir):
    hier = {k: (v[0], v[1], 0, "minecraft (vanilla seed)") for k, v in VANILLA.items()}
    for jar in sorted(os.listdir(mods_dir)):
        if not jar.endswith(".jar"):
            continue
        try:
            zf = zipfile.ZipFile(os.path.join(mods_dir, jar))
        except zipfile.BadZipFile:
            continue
        for info in zf.infolist():
            if not info.filename.endswith(".class") or info.file_size > 8_000_000:
                continue
            r = parse_class(zf.read(info))
            if r and r[0] not in hier:  # first definition wins (shaded copies ignored)
                hier[r[0]] = (r[1], r[2], r[3], jar)

    memo = {}

    def info_of(c, depth=0):
        """-> (is_te, is_inv, unknown_parent)"""
        if c in memo:
            return memo[c]
        if c is None or c == "java/lang/Object" or depth > 60:
            return (False, False, False)
        if c not in hier:
            return (False, False, True)
        sup, ifs, _, _ = hier[c]
        te = c == TE
        inv = c == INV
        unk = False
        for p in [sup] + ifs:
            t, i, u = info_of(p, depth + 1)
            te, inv, unk = te or t, inv or i, unk or u
        memo[c] = (te, inv, unk)
        return memo[c]

    rows, per_jar = [], Counter()
    unknown_te = defaultdict(set)
    for c, (sup, ifs, acc, jar) in hier.items():
        if jar.startswith("minecraft"):
            continue
        te, inv, unk = info_of(c)
        if acc & ACC_INTERFACE:
            continue
        if te and inv:
            chain, p = [], sup
            while p and p in hier and p != TE and len(chain) < 12:
                chain.append(p.rsplit("/", 1)[-1])
                p = hier[p][0]
            rows.append([jar, c.replace("/", "."), "abstract" if acc & ACC_ABSTRACT else "concrete",
                         "ISidedInventory" if info_of_sided(c, hier) else "IInventory", " > ".join(chain)])
            per_jar[jar] += 1
        elif unk and c.split("/")[-1].startswith("TileEntity"):
            unknown_te[jar].add(c.replace("/", "."))

    os.makedirs(out_dir, exist_ok=True)
    rows.sort()
    with open(os.path.join(out_dir, "containers.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["jar", "class", "abstract", "interface", "superclass_chain"])
        w.writerows(rows)
    with open(os.path.join(out_dir, "summary.txt"), "w") as f:
        f.write(f"Inventory TileEntity classes: {len(rows)} "
                f"({sum(1 for r in rows if r[2] == 'concrete')} concrete) across {len(per_jar)} jars\n\n")
        for jar, n in per_jar.most_common():
            f.write(f"{n:6d}  {jar}\n")
        f.write("\nTileEntity-named classes whose parent is in a jar we don't have (could not classify):\n")
        for jar in sorted(unknown_te):
            f.write(f"  {jar}: {len(unknown_te[jar])}\n")
    print(open(os.path.join(out_dir, "summary.txt")).read()[:4000])


def info_of_sided(c, hier, seen=None):
    seen = seen or set()
    if c in seen or c not in hier:
        return c == "net/minecraft/inventory/ISidedInventory"
    seen.add(c)
    if c == "net/minecraft/inventory/ISidedInventory":
        return True
    sup, ifs, _, _ = hier[c]
    return any(info_of_sided(p, hier, seen) for p in [sup] + ifs if p)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
