# Research notes (workspace setup, 2026-10-27)

Tags: [Certain] = checked against a file or source listed here. [Likely] = strong inference. [Guessing] = gap.

## Target pack
- [Certain] GTNH **2.9.0-RC-2**, manifest dated 2026-10-04 (`docs/data/2.9.0-RC-2.json`, from GTNewHorizons/DreamAssemblerXXL). 211 GitHub mods + 31 external mods.
- [Certain] Forge **10.13.4.1614**, Minecraft 1.7.10 (template `gradle.properties`).
- [Certain] The pack ships **InventoryBogoSorter 1.3.54-GTNH**, not Inventory Tweaks. The concept doc's "look at Inventory Tweaks" should read "look at InventoryBogoSorter". Its source is in `refs/src/InventoryBogoSorter`.
- [Certain] The pack includes **EndlessIDs 1.7.4** and **lwjgl3ify 3.0.37** (client runs on modern Java).

## Build toolchain
- [Certain] GTNH ExampleMod starter (2026-10-26): Gradle wrapper 9.7.1, `gtnhsettingsconvention` plugin 2.0.33, daemon JVM 25 (auto-downloaded via foojay), Jabel (modern syntax, Java 8 bytecode).
- [Certain] Spotless formatting is enforced unless `disableSpotless = true` (GTNHGradle 2.0.33 `CodeStyleModule`). Run `./gradlew spotlessApply` before `build`.
- [Certain] Dependency coordinates are `com.github.GTNewHorizons:<repo>:<version>:dev`. Exact RC-2 versions of ironchest, StorageDrawers, NotEnoughItems, GTNHLib and InventoryBogoSorter are already referenced by other GTNH mods' build files, so those artifacts exist.

## Layer 1: which blocks count as containers
Source: `tools/scan_containers.py` over 208 of 242 RC-2 jars → `docs/data/containers.csv`, `docs/data/summary.txt`.
- [Certain] 536 TileEntity classes implement IInventory (464 concrete) across 68 jars.
- [Certain] **Every GregTech machine and pipe** is `gregtech.api.metatileentity.BaseMetaTileEntity` / `BaseMetaPipeEntity`, which implements `ISidedInventory`. "Implements IInventory" would therefore treat every GT machine as a chest. → `is_container()` must be an **allow-list**, with GT Super/Quantum Chests handled through the MetaTileEntity inside the base class. [Guessing] how to identify those from the client; needs a look in GT5U source (`refs/src/GT5-Unofficial`).
- [Certain] Iron Chests: 11 chest classes (Iron, Gold, Diamond, Copper, Silver, Crystal, Obsidian, Dirt, Steel, Dark Steel, Netherite).
- [Certain] Storage Drawers, JABBA barrels, AE2 parts are ISidedInventory with non-chest semantics (one item type per slot). Exclude in early stages.
- [Certain] Hodgepodge shows 2 hits that are mixin classes, not real tile entities: the scan has some false positives.
- [Likely] Vanilla chest hierarchy in the scan is seeded by hand (Minecraft's jar isn't available here); only matters for mods extending vanilla classes.
- Not scanned (jar not downloadable here): 30 CurseForge/external mods incl. BiblioCraft, Thaumcraft, Extra Utilities, IC2, Witchery; 4 GTNH repos that return 404 publicly (Catwalks-2, Galaxy-Space-GTNH, harvestcraft, Automagy-GTNH). List: `docs/data/rc2-jars-not-downloaded.tsv`.

## Layer 2 / 7: reading and clicking modded chest GUIs
- [Certain] InventoryBogoSorter's `compat/DefaultCompat.java` declares, per Container class, which slot range is the chest (start index, count, row width) for ~30 mods, plus a mixin for Iron Chest containers (`mixins/late/ironchests/MixinIronChestContainer.java`). This is the best existing map for "read_all_slots(open_gui)".

## Layer 6: walking
- [Certain] No 1.7.10 Baritone. Baritone v1.2.19 (1.12.2) is cloned in `refs/src/baritone-v1.2.19` for its pathing design (`src/main/java/baritone/pathing`). Code can't be copied directly (different MC version, LGPL-3.0).

## Not verified yet
- [Likely] Forge reference source is the MinecraftForge `1.7.10` branch head; exact build number not confirmed as 1614. The real decompiled source comes from `./gradlew setupDecompWorkspace` on your PC.
