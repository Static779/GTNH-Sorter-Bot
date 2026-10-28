# Chest Sorter (GTNH 2.9.0-RC-2, Forge 1.7.10)

Client-side mod. Press one key and it reads every vanilla chest and Iron Chest near you, decides which chest holds which kind of item, combines partial stacks, and walks around moving stacks so each kind ends up together. Every chest it touches is checked before it closes.

**Reading chests:** in singleplayer (or when you host LAN) it reads every chest straight from your world in one tick, without walking or opening anything. On servers it walks to and opens each chest.

## Use
1. Put `chestsorter-<version>.jar` (not `-dev` or `-sources`) in your GTNH instance's `mods` folder (`<instance>\.minecraft\mods` in Prism).
2. Options → Controls → Chest Sorter → bind **Start / stop sorting** (unbound by default).
3. Stand among your chests with **at least one empty slot in your main inventory** and **one empty hotbar slot**.
4. Press the key. Press it again, press **Esc**, or touch **any movement key** to stop.

Try `/chestsorter plan` first: it opens and reads every chest and prints the plan, but moves nothing.

| Command (`/cs` works too) | Does |
|---|---|
| `/chestsorter run` | Full sort (same as the key) |
| `/chestsorter plan` | Survey and print the plan; moves nothing |
| `/chestsorter stop` | Stop now |
| `/chestsorter status` | What it is doing |
| `/chestsorter scan` | List nearby chests only |
| `/chestsorter item` | Show the held item's category, group, ore names and mod |
| `/chestsorter config` | Open the settings screen |
| `/chestsorter verbose` | Toggle step-by-step chat (saved) |

## How it decides
- **Category** of an item: its GregTech ore-dictionary prefix (`ingot`, `dustTiny`, `crushedPurified`, `ore`, ...; the 308 prefixes come from GT's own `OrePrefixes`). Recipe-group names (`listAllseed`, `craftingPiston`, `itemSkull`) are ignored. Items without a usable name go to their mod split by creative tab, e.g. `mod:minecraft:redstone`, `mod:gregtech:gregtech.main`; else `misc`.
- **Groups:** categories belong to groups, in layout order: ores (ore, rawOre, crushed...), dusts, metals (ingot, nugget, block), gems, plates, parts, wires, pipes, electronics, fluids, tools, wood, stone, glass, farming, food, mob drops, dyes, books. Anything not listed is grouped by mod (`mod:forestry`). You can edit the groups in the settings screen.
- **How narrow the chests are** depends on how many chests you have (class-based storage, as warehouses do it):
  1. one bucket per group; groups with fewer than `minStacksForOwnChest` (6) stacks are parked with their closest relative (or `misc`) so big groups get chests first,
  2. too few chests: the smallest bucket joins its closest relative, else `misc`. **Every item gets a chest**,
  3. spare chests: the biggest category is split out of its group into its own chest (dustImpure out of dusts, ingot out of metals...), biggest first, while chests last,
  4. a leftover of under 6 stacks joins its closest split-off sibling (nugget → ingot),
  5. chests still spare: parked groups get their own chest back.

  Every step is listed in the trace.
- **Which chest gets which bucket** balances four things:
  1. few stacks moved (a chest that mostly holds something keeps it),
  2. enough room (big categories get several chests),
  3. one category's chests side by side,
  4. related categories near each other (ore → rawOre → crushed → crushedPurified → dustImpure → dust → dustTiny; ingot ↔ nugget ↔ block; plate ↔ plateDouble ↔ ...; same-mod groups).

  `layoutWeight` sets the balance: 0 = only move what is out of place, 1 = balanced (default), higher = tidier layout even if more moves.
- **Moves**: whole stacks only, and only when the target chest has room for the whole stack. Otherwise the stack stays put.
- **Combining**: partial stacks of the same item in a chest are poured together the first time the run opens it (20 + 30 → 50). Chests no trip opens are visited at the end to combine theirs (`combineStacks`).
- **Trip order**: each trip starts at the nearest chest with something to move, usually the one you are standing at; if that chest is still open, it stays open.
- **Capacity**: before moving, the trace lists slots used and free, buckets against chests, and each bucket's free slots, and warns when a bucket will be nearly full.

## Why it can't scramble or lose items
- It only ever sends two kinds of click: pick up a whole stack, and put it down. No shift-click, no dropping, no clicks outside the window.
- Pick-up and put-down happen in the same game tick, so nothing sits on the cursor between ticks or when a chest closes.
- Picked stacks go only into inventory slots that were **empty when the run started**. Your own items, hotbar and armour are never touched.
- Before anything moves, the whole plan is replayed on a copy. If items would be created, lost, sent to the wrong category or not fit, nothing moves.
- Every click is checked against what the client sees straight away. Before **every chest closes** it waits for the server, then checks: item totals in the chest + your inventory + cursor are exactly what they were when it opened, every carried stack is in its slot, and every other inventory slot is unchanged. Any mismatch stops the run on the spot. (`verifyEveryTransfer` adds a server check after each single stack.)
- Before taking from a chest, it checks the chest still holds what the survey saw. If it changed, that trip is skipped.
- A chest it can't reach or open is skipped for the rest of the run: stacks meant for it go back where they came from and the other trips carry on. If even returning fails, they stay in your inventory and chat tells you which slot holds what, and where it came from.
- Items are compared by identity and count. NBT changes an item makes by itself (Tinkers' tools do) are logged, not treated as loss.
- At the end it compares, over every chest it touched, the totals from the survey with the totals seen at its final check (`finalRecheck` walks back and re-opens them instead).

## Stops by itself when
You take damage, health drops below 50%, a hostile mob is within 8 blocks, you press any movement/jump/sneak/attack/use/inventory key, any screen opens (chat, inventory), a chest screen closes, the hotbar selection changes, or you change world.

## When something goes wrong
Every run writes `.minecraft\chestsorter\trace-<date>-<time>.log`: every step, every path, every click (window, slot, slot contents, cursor), every check, the full plan and each item's category and ore names. Chat prints the file's path at the end of each run. Send me that file.

## Settings
In game: **Mods → Chest Sorter → Config**, `/cs config`, or bind **Open settings** in Controls. Every setting has a tooltip and a reset button; Done saves, and the next run uses the new values. The file is `config/chestsorter.cfg`.

| Section | Settings |
|---|---|
| Sorting | scan radius (16), stacks for a chest of its own (6), layout tidiness (1.0), max trips (300), category groups |
| Movement | walk to chests, sprint, reach (4.0), path search limit, stuck after, new paths before skipping |
| Safety and pacing | ticks between transfers (1), server wait before checks (4, raise on laggy servers), open timeout, check every transfer, re-check chests at the end, hostile radius (8), health fraction (0.5) |
| Debug | trace file, every step in chat |

Group lines look like `metals: ingot*, nugget*, block` (a trailing `*` matches any name starting with it). `/cs item` shows which category and group the held item lands in. Older config files are upgraded automatically (`configVersion` 3); your values are kept.

## Limits (v1)
- Containers: vanilla chests (incl. trapped, double) and Iron Chests (all tiers except Dirt Chest 9000).
- Walking: no doors, ladders, water or block breaking. Steps up 1 block, drops up to 3. Unreachable chests are skipped and listed; run again after fixing access to finish them.
- If two full chests each hold the other's items, neither can move first; those stacks stay put (add one empty slot or chest).
- Chests fed or emptied by hoppers, pipes or conveyors change during the run. The checks notice and stop (or skip that trip); nothing is lost, but sort those areas with the automation paused.

## Build
**GitHub CI:** every push to `main` runs GTNH's build-and-test workflow (compile, formatting, server start). The jar is on the workflow run (public repo). If a build fails, run **Actions → Diagnose build**: it writes the exact errors and formatter diff as annotations.

**On your PC (Windows):**
```
gradlew.bat spotlessApply
gradlew.bat build
```
**Without Minecraft:** `tools/test-core.sh` runs the logic tests (categorizing, grouping, assigning, planning on 6000 random chest setups, pathfinding). `tools/check-stubs.sh` is a quick typo check. `tools/replay-trace.sh <trace.log> [minStacks] [layoutWeight] [groups-file]` re-plans a real run from its trace with the current code, so grouping changes can be checked against your actual chests.

## Layout
| Path | What |
|---|---|
| `src/main/java/.../core/` | Pure logic (no Minecraft): categorizer, assigner, planner, plan checker, pathfinder |
| `src/main/java/.../client/` | In-game: scanner, run executor, walker, safety, commands, trace |
| `docs/research-notes.md` | Findings and decisions, tagged by confidence |
| `docs/data/` | RC-2 manifest, mod list, container scan of the pack |

## Credits
Rotation smoothing follows [LootGameSolver](https://github.com/KyroXova/LootGameSolver) by KyroXova (MIT). Chest slot layouts were checked against [InventoryBogoSorter](https://github.com/GTNewHorizons/InventoryBogoSorter) (LGPL-3.0).

## Servers
Singleplayer, your own server, or public servers. Many public servers ban automated movement and clicking; check the rules first.

## License
LGPL-3.0 (`LICENSE`), which builds on the GPL-3.0 (`COPYING`).
