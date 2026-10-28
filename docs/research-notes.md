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
- [Certain] Dependency coordinates are `com.github.GTNewHorizons:<repo>:<version>:dev`. The exact RC-2 versions are referenced in other GTNH mods' `dependencies.gradle`: ironchest 6.1.13 (InventoryBogoSorter), StorageDrawers 2.2.31-GTNH (GT5U), NotEnoughItems 2.8.155-GTNH (AE2, GT5U, lwjgl3ify), GTNHLib 0.11.52 (AE2, GT5U), InventoryBogoSorter 1.3.54-GTNH (AE2).
- [Likely] So those artifacts resolve from GTNH's Maven. Not tested yet: all are commented out in our `dependencies.gradle`.

## Layer 1: which blocks count as containers
Source: `tools/scan_containers.py` over 208 of 242 RC-2 jars → `docs/data/containers.csv`, `docs/data/summary.txt`.
- [Certain] 536 TileEntity classes implement IInventory (464 concrete) across 68 jars.
- [Certain] GT's machine block creates its tile entity through `GregTechAPI.createTileEntity(meta)` (`BlockMachines.java:681`), which gives a `BaseMetaTileEntity` (machines) or `BaseMetaPipeEntity` (pipes). Both implement `ISidedInventory`. "Implements IInventory" would therefore treat GT machines and pipes as chests. → `is_container()` must be an **allow-list**.
- [Certain] GT Super/Quantum Chests are `MTESuperChest` / `MTEQuantumChest` (both under `gregtech/common/tileentities/storage/`, base `MTEDigitalChestBase`), held inside a `BaseMetaTileEntity`. [Guessing] how the client reads which MetaTileEntity a base holds; not researched yet.
- [Certain] Bartworks, GT++ and kubatech (bundled in the same jar) add 7 more inventory tile entities of their own; see `containers.csv`.
- [Certain] Iron Chests: 11 chest classes (Iron, Gold, Diamond, Copper, Silver, Crystal, Obsidian, Dirt, Steel, Dark Steel, Netherite).
- [Certain] Storage Drawers and JABBA barrels implement ISidedInventory (scan). [Likely] Their slots don't behave like chest slots (each drawer/barrel holds one item type in bulk), so they stay off the allow-list for now.
- [Certain] Hodgepodge shows 2 hits that are mixin classes, not real tile entities: the scan has some false positives.
- [Likely] Vanilla chest hierarchy in the scan is seeded by hand (Minecraft's jar isn't available here); only matters for mods extending vanilla classes.
- Not scanned (jar not downloadable here): 30 CurseForge/external mods incl. BiblioCraft, Thaumcraft, Extra Utilities, IC2, Witchery; 4 GTNH repos that return 404 publicly (Catwalks-2, Galaxy-Space-GTNH, harvestcraft, Automagy-GTNH). List: `docs/data/rc2-jars-not-downloaded.tsv`.

## Layer 2 / 7: reading and clicking modded chest GUIs
- [Certain] InventoryBogoSorter's `compat/DefaultCompat.java` declares, per Container class, which slot range is the chest (start index, count, row width): vanilla containers plus 28 mod-guarded blocks. This is the best existing map for "read_all_slots(open_gui)".
- [Certain] Iron Chests: its mixin (`mixins/late/ironchests/MixinIronChestContainer.java`) treats slots 0 to rows × row length as the chest, row width = the tier's row length, and skips the Dirt chest.

## Layer 6: walking
- [Likely] No 1.7.10 port of Baritone exists. Baritone v1.2.19 (1.12.2) is cloned in `refs/src/baritone-v1.2.19` for its pathing design (`src/main/java/baritone/pathing`).
- [Certain] Baritone's license is not plain LGPL-3.0: its README calls it "LGPL-3.0 with anime exception", and `LICENSE-Part-2.jpg` is a "No anime" image. [Guessing] what that extra term means legally. → Use Baritone as a design reference only; don't copy its code.

## Not verified yet
- [Likely] Forge reference source is the MinecraftForge `1.7.10` branch head; exact build number not confirmed as 1614. The real decompiled source comes from `./gradlew setupDecompWorkspace` on your PC.

## Decisions (2026-10-27, from your answers)
- Build route: GitHub CI (GTNH shared `build-and-test.yml`). [Certain] It checks out full history (tags needed for the version) and uploads `build/libs/` only for public repos.
- Your setup: Windows, Prism Launcher, instance on Java 21, IntelliJ installed.
- First allow-list: vanilla chests and Iron Chests (all tiers). GT Super/Quantum Chests, drawers and barrels later.
- Use: singleplayer, own server and public servers. Rate limiting between clicks stays for desync safety; nothing will be built to hide the mod from server staff. Check each server's rules.
- Ignore the 34 jars that couldn't be downloaded.
- Names kept: Chest Sorter / `chestsorter` / `com.whalestorm.chestsorter` / author Whalestorm.
- Toggle key ships unbound.
- License: LGPL-3.0 (`LICENSE`, with GPL-3.0 in `COPYING`). Compatible with reusing InventoryBogoSorter (LGPL-3.0) code with credit.

## CI status
- [Certain] 2026-10-27: `main` @ a955a2b builds green on GTNH's build-and-test workflow (compile, Spotless/Checkstyle, dedicated-server smoke run). Artifacts: `chestsorter-a955a2b.jar` (the one to install), `-dev`, `-sources`.
- [Certain] Only Spotless failed on the first run (one wrapped call in Config.java); fixed by applying its diff.
- [Certain] This session can push branches to the repo but not tags or branch deletions.
- [Certain] 2026-10-27: stage 0 tested in game by you: mod loads, key prints SCAN / IDLE.
- [Certain] 2026-10-27: stage 1 (container scanner) @ 7cb1a85 builds green incl. server run. Not yet run in game.

## Full build (2026-10-27)
- [Certain] LootGameSolver (KyroXova, MIT, 1.7.10 GTNH client mod) opens/clicks blocks with `playerController.onPlayerRightClick(player, world, held, x, y, z, 1, Vec3(top centre))` after `swingItem()`, and turns with at most 35° yaw / 25° pitch per tick. We use both. It moves by pushing velocity straight at the target (or flying); we follow an A* path with vanilla movement input instead.
- [Certain] Forge 1.7.10 calls the held item's `onItemUseFirst` before a block's `onBlockActivated`, so a held tool can act on a chest. Runs switch to an empty hotbar slot.
- [Certain] Iron Chest slots are `ValidatingSlot`s (`isItemValid` per tier); deposits check `isItemValid` per slot.
- [Certain] Vanilla stores mouse keybinds as code − 100; the safety check reads raw keyboard/mouse state, so it still sees you while the mod drives movement input.
- [Certain] etfuturum and freecam mixin `MovementInputFromOptions`; the run replaces the player's `movementInput` with its own subclass for the run and restores it after.
- [Certain] Categories use GT5U's 308 `OrePrefixBuilder` names (bundled as `assets/chestsorter/gt-ore-prefixes.txt`).
- Tests: `tools/test-core.sh` — 8235 checks, including 3000 random chest setups replayed by PlanChecker. A deliberately broken planner (whole-stack fit check removed) fails 930 checks, including item-loss detection.
- [Guessing] Not verifiable here: real timing on servers (settleTicks 5 may be short on laggy servers), walking over unusual GTNH blocks, and chests that change while the run is going.
- [Certain] 2026-10-27: full sorter @ 20f7c19 builds green (compile, Spotless, Checkstyle, server run). Formatting fixes were taken from CI's own spotlessApply output after checking each file's non-whitespace content was unchanged (one file differed only by doc-comment wrapping).
- Not yet run in game.
