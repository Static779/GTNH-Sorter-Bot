# Chest Sorter (GTNH 2.9.0-RC-2, Forge 1.7.10)

Client-side mod that will sort nearby chests by item category. Built on the official GTNH ExampleMod starter.

**Current stage: 0** — mod loads and the toggle key prints its state in chat. No world access, no movement, no clicks.

## Build (on your PC)
Needs internet the first time (Gradle downloads JDK 25, Minecraft 1.7.10, Forge and GTNH tooling).

```
./gradlew spotlessApply      # auto-format; build fails on formatting otherwise
./gradlew build              # jar lands in build/libs/
```
Windows: use `gradlew.bat` instead of `./gradlew`.

Optional dev setup: `./gradlew setupDecompWorkspace`, then open the folder in IntelliJ IDEA.

## Test stage 0
1. Copy `build/libs/chestsorter-<version>.jar` (not the `-dev` or `-sources` jar) into a **copy** of your GTNH 2.9.0-RC-2 instance's `mods` folder.
2. Start the game, open a throwaway singleplayer world.
3. Options → Controls → "Chest Sorter" → bind "Start / stop sorting" (unbound by default).
4. Press it: chat shows `[Chest Sorter] SCAN`. Press again: `[Chest Sorter] IDLE`.

Report: did it load (Mods list shows "Chest Sorter"), and did both chat lines appear?

## Layout
| Path | What |
|---|---|
| `src/main/java/.../chestsorter/core/` | Pure Java logic, no Minecraft imports. Checked by `tools/check-core.sh`. |
| `src/main/java/.../chestsorter/client/` | Client code: keybinds, tick/key handlers. |
| `docs/research-notes.md` | Findings so far, tagged by confidence. |
| `docs/data/` | RC-2 manifest, mod list, container scan results. |
| `tools/check-stubs.sh` | Typo check against hand-verified 1.7.10 API stubs (not a real build). |
| `tools/scan_containers.py` | Scans mod jars for inventory tile entities. |

## Rules
Singleplayer or your own server only. On a public server this may count as botting.
