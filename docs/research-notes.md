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

## Speed pass (2026-10-28, from your traces)
- [Certain] trace-20261028-001631: walking 2352 ticks, opening 1276 (13.4/chest), each stack ~10 ticks (5 settle + 3 delay).
- [Likely] The stuck re-pathing toward -1870 60 4555 came from the feet-height calculation (floor(minY+0.01)): standing on a chest (0.875 tall) put the start node inside the chest block. Now floor(minY+0.5).
- Server check moved from every stack to once per chest visit (before close); clicks still checked against the client view immediately.
- [Certain] c0ab09d builds green (compile, Spotless, server run). Not yet run in game.

## Category layout (2026-10-28, from your request)
- Your choice: all four ideas weighted (fewer moves, room, same category together, related nearby); small categories merge into their nearest relative.
- Categorizer: recipe-group prefixes (list, crafting, item, ...) ignored; mod buckets split by creative tab. On your trace's 870 items, 52 change category (13 list → seed, 5 crafting → book, ...).
- Assigner: weighted cost = stacks moved + 4 × slots short + layoutWeight × (apart + 0.5 × related distance); start from current contents, place categories with no chest in the cheapest empty chest, then single changes + best swap per pass. Deterministic; capped at 60k evaluations (60 chests ≈ 0.2 s here).
- Relations: GT processing chains (my reading of GT's ore processing order) [Likely], name nesting, same mod.

## Grouping, fixes and settings screen (2026-10-28, from your 4 traces)
Why it looked random:
- [Certain] trace-20261028-012205: 174 fine categories (110 of them 1–2 stacks) for 48 chests. The old merge only joined categories that had a relative; about 80 had none (mod tabs, odd ore prefixes like "anti", "ardite"), so those items stayed wherever they were. Half-sorted chests full of leftovers look random.
- Research basis: Inventory Tweaks sorts with a hierarchical category tree (nested categories matched by id, damage or ore dict) [Certain, inventory-tweaks.readthedocs.io]; warehouses use class-based storage: group products into a few classes, give each class a zone, sized to the space [Certain, scitepress.org/Papers/2019/79524]. I combined both.
- New: `Taxonomy` (core). Categories → groups (editable lines, layout order) → buckets sized to the chest count: park small groups, merge when chests are short (nothing is left without a chest; unrelated → `misc`), split the biggest categories out when chests are spare, fold leftovers into a sibling, give parked groups chests back if any remain. The Assigner then places buckets with the same weighted cost as before.
- Replaying the 012205 trace's 880 item keys (no slot data in old traces, so one stack per key): 48 buckets, nothing unassigned, plan check OK. New traces log each chest's contents (`Contents #id:` lines) so `tools/replay-trace.sh` can replay real runs.

Other issues in the traces:
- [Certain] 012447 stopped with "Alumite Hammer 1 -> 0, Alumite Hammer 0 -> 1": the hammer in your inventory (key 3) changed its NBT mid-visit [Likely: Tinkers' tool data updated by the server]. Checks now compare by item identity + count (NBT ignored, damage ignored for non-subtype items) and log NBT-only changes.
- [Certain] 012605: chest #26 sits on top of #27. The path search said "already in reach" from the block centre, the real position was out of reach → "0 steps", and the run returned everything and ended. Now: plan 1.0 block inside reach; if a path ends out of reach, step straight toward the chest over standable ground (max 20 ticks), then re-path.
- [Certain] One unreachable chest used to end all remaining trips. Now that chest is skipped for the rest of the run, its stacks go back, other trips continue, and the end report says "X of Y trips ran" instead of "chests differ from the plan".
- Timing in 012605: one stack per tick, chest opens ~9 ticks, walks ~12 ticks average.

Settings screen:
- [Certain] FML 1.7.10: `@Mod(guiFactory=…)` → `IModGuiFactory.mainConfigGuiClass()`, built by GuiModList via a `(GuiScreen)` constructor; `GuiConfig(parent, elements, modID, worldRestart, mcRestart, title, title2)`; `new ConfigElement(category)` gives a category button; on Done it posts `ConfigChangedEvent.OnConfigChangedEvent` on the FML bus and the mod re-reads/saves itself. Checked in FML/Forge 1.7.10 sources.
- Number settings that older files stored as text (`reach`, `layoutWeight`, `minHealthFraction`) are converted to real number entries so the screen validates them; values kept.
