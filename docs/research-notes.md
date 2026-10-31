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

## Faster reading and small optimizations (2026-10-28)
Where run time went (trace 012605, 3511 ticks): walking 1610, opening chests 1168 (9.0 ticks each × 130), checks before closing 410, stack transfers 322 (1 tick each). The survey alone took 838 ticks (42 s) for 48 chests.

Ways to read chests without opening them, checked in source:
- [Certain] Singleplayer / LAN host: the game's built-in server runs in the same process; `MinecraftServer.getServer().worldServerForDimension(d).getTileEntity(x,y,z)` gives each chest's real inventory (exact slots, counts, NBT). Read on the server thread (ServerTickEvent on the FML bus) and copied. Now the default survey there (`localRead`); any chest it can't read is opened instead.
- [Certain] HoloInventory 2.5.17-GTNH (client and server in the pack): every tick the server ray-traces where you look (within reach) and sends that block's non-empty stacks with counts, no slot numbers; glasses only gate drawing. Client caches it in `Renderer.tileInventoryMap` (merged per item by default). Usable on GTNH servers by looking, not opening. Not built yet.
- [Certain] FindIt 1.4.7 (T key): server returns positions only (max 20, 10-tick cooldown) for one item per request. Not usable for a catalogue.
- [Certain] Iron Chests sync only their top 8 stacks to clients (for crystal chest display); vanilla chests sync nothing until opened.
- [Guessing] Own server with this mod installed there: a request/response packet could return every chest in range. Not built.

Built:
- Combining partial stacks (core `Combiner`, deterministic, 5000 random chests tested): pick up, click the same item's partial stack, put the rest back, all in one tick; checked per pour and by the server check before close. Never in plan-only runs.
- Opening: settle 1 tick and two consecutive equal reads (was 2 + one skipped tick): about 4 ticks saved per open.
- Trips: each trip starts at the nearest chest with something to move (from where the last trip ended); a chest that the next trip starts at is left open. On the reconstructed 012605 room: 61 trips instead of 75 for the same 852 stack moves, 18 trips start at the already-open chest.
- Capacity report before any move.

## HoloInventory survey and the sweep (2026-10-28)
HoloInventory survey (servers): walk into reach, aim at the chest until the game's own look ray has been on it for settleTicks, then read HoloInventory's client cache (`Renderer.tileInventoryMap`, by reflection). [Certain from source] its server resends a block's contents whenever they differ from what that player last got, so a cache entry that did not change is still current. Totals are packed into full stacks; picks are matched to live slots by item and count after combining. No data → that chest is opened; 3 misses and no hit → the rest are opened. Not testable here (needs a server).

Sweep (`tools/sweep.sh`, 5 seeds per row, items from trace 012605, walls of double chests; one setting varied from: 48 chests, 60% fill, 400 item types, 70% mess, 30% partial stacks, 13 carry slots, minStacks 6, layout 1, keepFree 15%):
- Fixed by this round: layoutWeight 3 left 25.6 stacks with no room (capacity weight 4 → 50: now 0.2); 4.4 of 37 buckets were nearly full (<10% free) at 60% fill (keepFree 15%: 1.0); 96 chests left 7.8 stacks (now 0.6).
- Still short: rooms ≥ ~85% full strand stacks (85%: 8.8, 95%: 101.6) because whole-chest buckets waste the tail of each bucket's last chest and full chests can't swap; small item-heavy rooms (16 chests, 82% full after one stack per type) strand ~33 and put 17% of stacks in misc. Few carry slots multiply walking (3 slots: 6964 blocks / 417 trips; 27 slots: 2677 / 82). Buckets spanning chests > 6 blocks apart: ~3 of 33. Plenty of chests and many tiny groups → many near-empty chests (96 chests at 30%: 18.8). Planning 96 chests takes ~2.5 s (now on a background thread, so no freeze).
- 205 plans, 0 PlanChecker problems.

## Big sweep: 1,116 rooms (2026-10-28)
`tools/sweep.sh items.tsv 8 500` (77 one-at-a-time settings × 8 rooms + 500 rooms with every setting random): room shape (walls, row, towers, scattered, separate rooms), chest sizes, 8–150 chests, fill 20–97%, 30–1,500 item types, size skew, item mix (natural / GT-only / mod-only), unstackable share, mess, partial stacks, free inventory slots 1–27, stacks for own chest, tidiness, keep-free, start position. 40 measures per room. Results: `docs/data/sweep-1116-rooms.csv`.
- [Certain] 0 of 1,116 plans failed the safety replay; 0 of the rooms planned twice gave a different plan. Median planning 639 ms, slowest 8.0 s (150 chests; now on a background thread).
- Random rooms, share with each shortfall: groups' chests not touching (<75% together) 88%; over 15 min on a server 64%; over 4 blocks walked per stack 66%; 3+ groups under 10% free 50%; 5+ near-empty chests 48%; misc over 10% 15%; planning over 3 s 17%; stacks stranded over 1% 8.8%.
- Drivers (Spearman, random rooms): stranding ← fill (0.32), fewer stacks-for-own-chest (−0.24); misc ← fewer chests (−0.42), over ~20 item types per chest (misc 10.7%); scattering ← more chests (0.46), less tidiness; time ← chests (0.63), fill, mess; walking ← chests, fewer free slots.
- Stranding is near zero below 75% fill; 85–92% full: 12–50% of rooms strand >1%; 92%+: 25–100%.
- One-at-a-time: free inventory slots dominate run time (1 slot 35 min, 13 slots 15 min, 27 slots 11 min, 48 chests); spread-out room shapes walk 2× more (scattered 6.9 blocks/stack vs towers 2.6); survey method matters little next to trips (singleplayer read saves well under a minute at 48 chests by the model; the real trace measured 42 s).
- Changed from this: default tidiness 1 → 2 (groups whose chests touch 54% → 74%, same moves, nothing stranded). Config v4 moves an untouched 1.0 to 2.0.
- Time model: walking 4.2 ticks per block beyond reach (trace 012605: 1,609 ticks over 381 path steps), open 5, check 5, 1 per stack or pour. [Likely] it underestimates the survey (model ~0.3 min vs measured 0.7 min for 48 chests).

## Whole-route planner (2026-10-28, from your question)
Question: should it compute the shortest way to reach the sorted layout? [Certain] Exactly, no: moving stacks with a carry limit is a capacitated pickup-and-delivery problem, which contains the travelling-salesman problem (NP-hard); the state space for a 48-chest room is astronomically large.
What it does now: plans one route over all chests, cut into trips that start and end empty-handed. Next chest = the one minimising distance − 0.75 × (stacks to put in + take out). At each visit: put in, take out (nearest destination first, only with room promised at a chest of its group), put in again (swaps between full chests; only tried when nothing else can move, and a failed swap trip is dropped). After max(8, 2 × carry slots) visits a trip only delivers. PlanChecker replays visits op by op (take matches slot, carry limit, each stack put in once into its group, fits, empty at end, totals and end state match).
Client: per visit GoTo → Open (reused if open) → combine → match picks to live slots → ops → verify → close (skipped if the next visit is the same chest). Picks with no live match or no empty carry slot are skipped, not failed; anything still carried at the end of a trip goes back to its source.
Measured (`tools/sweep.sh`, 77 one-at-a-time rooms; old planner → new): walking 324,194 → 223,574 blocks (−31%), modelled time 1,283 → 978 min (−24%), stacks stranded 124 → 120, 0 safety failures. Tuning: work value 3 → walk 253,291; 0.75 best; trip cap 16/3× carry → 222,060 walk but 142 stranded (kept 8/2×).
Exact comparison (`tools/optgap.sh`, 369 solvable rooms of 3–6 chests and ≤ 9 stacks, Dijkstra over position × each stack's state): planner/shortest mean 1.090, median 1.036, 90th percentile 1.259, worst 1.707; equal on 141 of 373. The MST-based lower bound used in the sweep is weak (route ≈ 4.3× it), so it says little about the gap on big rooms. [Guessing] the gap grows somewhat with room size; not measurable exactly.
Replay of the reconstructed 48-chest room: 5 trips, 302 visits, 853 stack moves, 0 stranded, check OK.

## Extra-chest what-if and room to grow (2026-10-28, from your request)
Request: a setting to see how much extra chests would help. Built `core/WhatIf`: re-plans with N imaginary empty chests (your most common chest size, placed one at a time beside an existing chest, nearest the room's middle; walls not checked) and reports per N: groups and chests used, stacks left over (no room or no chest), groups under 10% free, misc share, categories per chest, free slots, moves, walking and minutes (same time model as the sweep). Shown by plan-only runs (`/cs plan`, `/cs whatif [n ...]`); setting `extraChests` (default "6, 12, 24", up to 4 numbers, empty = off). Normal runs don't compute it (it re-plans once per number; ~1.2 s each for 48 chests), but the nearly-full warning points to `/cs whatif`.
Found while testing it: extra chests didn't fix nearly full groups. [Certain, from debugging the 48-chest room] The assignment cost only counted slots short of the stacks, not of the keep-free room, so a group of 53 stacks kept a single 54-slot chest and the extra chests went to finer splits or stayed unused for layout. Added a growing-room term (2 per slot short of stacks / (1 − keepFree)). Sweep of 137 rooms, weight 0 / 0.5 / 2 / 5: groups under 10% free 303 / 206 / 146 / 120; stacks left over 730 / 736 / 732 / 753; moves within 0.3%; 0 safety failures. Chose 2.
48-chest room (reconstructed trace): everything fits now; nearly full 4 → 2 (+6) → 2 (+12) → 1 (+24); categories per chest 4.2 → 2.6. The last nearly full group (crushed ores, 53 stacks in one 54-slot chest) keeps one chest because the spare chest sits away from it and layout outweighs it. [Likely] a chest placed right beside it would fix it.
Tests: 12 crowded rooms (~93% full): +24 chests → stacks left over 350 → 0, nearly full 49 → 0.

## Inventory Bogosorter (2026-10-31, from your pointer)
[Certain] GTNH 2.9.0-RC-2 ships InventoryBogoSorter 1.3.54-GTNH, side BOTH (DreamAssemblerXXL releases/manifests/2.9.0-RC-2.json; HoloInventory 2.5.17-GTNH also BOTH). It replaces Inventory Tweaks in GTNH; the project brief's "use Inventory Tweaks' click code" doesn't apply. Source: GTNewHorizons/inventorybogosorter; the drop-off, sort packet and config are unchanged between tag 1.3.54-GTNH and 1.3.55-GTNH.
- Sorting is server-side: the client sends CSort (slot groups + rule ids), the server's SortHandler rewrites the open container and syncs it. No click simulation, so it is no reference for our clicking.
- Drop-off (credited to the DropOff mod): one packet; the server scans tile entities within `dropoffRadius` (default 4 blocks, synced from the server) that are TileEntityChest or whose name contains Chest/Barrel/Drawer/Crate/..., and moves every stack from player slots 9–35 (hotbar only if enabled; pinned slots skipped) into a container that already holds the same item (item + damage + exact NBT): onto partial stacks first, then an empty slot only if that item is already there.
- Why the sorter doesn't use drop-off for its trips: it takes the player's own non-pinned items too (breaks "your items are never touched"), it picks destinations by "already holds this item" (a stray stack in a wrong chest attracts more), it can't start a new item in an empty chest, and it reaches 4 blocks. Clicks are ~1 tick per stack; walking is the cost, which drop-off doesn't change.
- Where drop-off fits: after a sort each item lives only in its group's chest, so drop-off near the wall then refills the right chests on its own (for items already stored).
- Used now: `ClientEventHandler.sort(Container, SlotAccessor, int)` (public static) with no slot sorts the container's single non-player slot group. The sorter calls it by reflection on each chest's last visit (`bogoSortChests`), waits settleTicks, then VerifyVisit checks totals and inventory as usual. Untested in game.
- Interference: our clicks go through playerController.windowClick, not GUI mouse handlers, so Bogosorter's Ctrl/Space/Alt click shortcuts don't fire on them [Likely]. Auto-refill triggers on an item breaking or running out in the hotbar; nothing in a run does that [Likely]. Pressing Bogosorter's drop-off key during a run would dump carried stacks into nearby matching chests; the next check would stop the run, with nothing lost.
