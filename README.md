# Chest Sorter (GTNH 2.9.0-RC-2, Forge 1.7.10)

Client-side mod that will sort nearby chests by item category. Built on the official GTNH ExampleMod starter.

**Current stage: 0** — mod loads and the toggle key prints its state in chat. No world access, no movement, no clicks.

## Build
**Main route: GitHub CI.** Every push to `main`/`master` runs GTNH's shared build-and-test workflow (`.github/workflows/build-and-test.yml`), which compiles the mod and starts a test server. On a **public** repo the jar is attached to the workflow run (artifact `<repo id>-build-libs`); on a private repo the workflow skips that upload, so build locally to get the jar. Push tags too (`git push --tags`): the mod version comes from the latest tag.

**On your PC (Windows), optional.** Needs internet the first time (Gradle downloads JDK 25, Minecraft 1.7.10, Forge and GTNH tooling).
```
gradlew.bat spotlessApply      # auto-format; the build fails on formatting otherwise
gradlew.bat build              # jar lands in build\libs\
```
To read or edit the code in IntelliJ IDEA: run `gradlew.bat setupDecompWorkspace`, then open the folder in IntelliJ.

## Test stage 0
1. Copy `chestsorter-<version>.jar` (not the `-dev` or `-sources` jar) into the `mods` folder of a **copy** of your GTNH 2.9.0-RC-2 Prism instance (`<instance>\.minecraft\mods`).
2. Start the game, open a throwaway singleplayer world.
3. Options → Controls → "Chest Sorter" → bind "Start / stop sorting" (unbound by default).
4. Press it: chat shows `[Chest Sorter] SCAN`. Press again: `[Chest Sorter] IDLE`.

Report: did it load (Mods list shows "Chest Sorter"), and did both chat lines appear?

## Layout
| Path | What |
|---|---|
| `src/main/java/.../chestsorter/core/` | Pure Java logic, no Minecraft imports. Checked by `tools/check-core.sh`. |
| `src/main/java/.../chestsorter/client/` | Client code: keybinds, tick/key handlers. |
| `docs/research-notes.md` | Findings and decisions, tagged by confidence. |
| `docs/data/` | RC-2 manifest, mod list, container scan results. |
| `tools/check-stubs.sh` | Typo check against hand-verified 1.7.10 API stubs (not a real build). |
| `tools/scan_containers.py` | Scans mod jars for inventory tile entities. |

## Servers
Built for singleplayer, your own server, and public servers. Many public servers ban automated movement and inventory clicking; check each server's rules before turning it on there.

## License
LGPL-3.0 (`LICENSE`), which builds on the GPL-3.0 (`COPYING`).
