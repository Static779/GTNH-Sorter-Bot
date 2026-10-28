# Chest Sorter (GTNH 2.9.0-RC-2, Forge 1.7.10)

Client-side mod. Press one key and it walks to every vanilla chest and Iron Chest near you, reads what is inside, decides which chest holds which kind of item, and moves stacks so each kind ends up together. Then it re-opens every chest it touched and checks nothing was lost.

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
| `/chestsorter item` | Show the held item's category, ore names and mod |
| `/chestsorter verbose` | Toggle step-by-step chat |

## How it decides
- **Category** of an item: its GregTech ore-dictionary prefix (`ingot`, `dustTiny`, `plateDouble`, `ore`, ...; the 308 prefixes come from GT's own `OrePrefixes`), else `mod:<modid>`, else `misc`.
- **Chest category**: whatever fills most of its slots. Categories without a chest get an empty chest, nearest first. Categories that still have no chest are **left where they are**.
- **Moves**: whole stacks only, and only when the target chest has room for the whole stack. Otherwise the stack stays put.

## Why it can't scramble or lose items
- It only ever sends two kinds of click: pick up a whole stack, and put it down. No shift-click, no dropping, no clicks outside the window.
- Pick-up and put-down happen in the same game tick, so nothing sits on the cursor between ticks or when a chest closes.
- Picked stacks go only into inventory slots that were **empty when the run started**. Your own items, hotbar and armour are never touched.
- Before anything moves, the whole plan is replayed on a copy. If items would be created, lost, sent to the wrong category or not fit, nothing moves.
- Every click is checked against what the client sees straight away. Before **every chest closes** it waits for the server, then checks: item totals in the chest + your inventory + cursor are exactly what they were when it opened, every carried stack is in its slot, and every other inventory slot is unchanged. Any mismatch stops the run on the spot. (`verifyEveryTransfer` adds a server check after each single stack.)
- Before taking from a chest, it checks the chest still holds what the survey saw. If it changed, that trip is skipped.
- If it can't reach a target while carrying, it takes the items back to where they came from. If even that fails, they stay in your inventory and chat tells you which slot holds what, and where it came from.
- At the end it compares, over every chest it touched, the totals from the survey with the totals seen at its final check (`finalRecheck` walks back and re-opens them instead).

## Stops by itself when
You take damage, health drops below 50%, a hostile mob is within 8 blocks, you press any movement/jump/sneak/attack/use/inventory key, any screen opens (chat, inventory), a chest screen closes, the hotbar selection changes, or you change world.

## When something goes wrong
Every run writes `.minecraft\chestsorter\trace-<date>-<time>.log`: every step, every path, every click (window, slot, slot contents, cursor), every check, the full plan and each item's category and ore names. Chat prints the file's path at the end of each run. Send me that file.

## Settings
`config/chestsorter.cfg`: `scanRadius` (16), `walking` (true), `sprint` (true), `reach` (4.0), `clickDelayTicks` (1 = one stack per tick), `settleTicks` (4, raise on laggy servers), `verifyEveryTransfer` (false; true = also check after every single stack, much slower), `finalRecheck` (false; true = walk back and re-open every touched chest at the end), `hostileRadius` (8), `minHealthFraction` (0.5), `maxTrips` (300), `traceFile` (true), `verboseChat` (false). Older config files get the new speed defaults automatically (`configVersion` 2); your other settings are kept.

## Limits (v1)
- Containers: vanilla chests (incl. trapped, double) and Iron Chests (all tiers except Dirt Chest 9000).
- Walking: no doors, ladders, water or block breaking. Steps up 1 block, drops up to 3. Unreachable chests are skipped and listed.
- If two full chests each hold the other's items, neither can move first; those stacks stay put (add one empty slot or chest).
- Chests fed or emptied by hoppers, pipes or conveyors change during the run. The checks notice and stop (or skip that trip); nothing is lost, but sort those areas with the automation paused.

## Build
**GitHub CI:** every push to `main` runs GTNH's build-and-test workflow (compile, formatting, server start). The jar is on the workflow run (public repo). If a build fails, run **Actions → Diagnose build**: it writes the exact errors and formatter diff as annotations.

**On your PC (Windows):**
```
gradlew.bat spotlessApply
gradlew.bat build
```
**Without Minecraft:** `tools/test-core.sh` runs the logic tests (categorizing, assigning, planning on 3000 random chest setups, pathfinding). `tools/check-stubs.sh` is a quick typo check.

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
