# Chest Sorter (GTNH 2.9.0-RC-2, Forge 1.7.10)

Client-side mod that will sort nearby chests by item category. Built on the official GTNH ExampleMod starter.

**Current stage: 1** — the toggle key scans for vanilla chests and Iron Chests near you and lists them in chat. No walking, no clicking, no chest opening.

## Build
**Main route: GitHub CI.** Every push to `main`/`master` runs GTNH's shared build-and-test workflow (`.github/workflows/build-and-test.yml`), which compiles the mod and starts a test server. On a **public** repo the jar is attached to the workflow run (artifact `<repo id>-build-libs`); on a private repo the workflow skips that upload, so build locally to get the jar. Push tags too (`git push --tags`): the mod version comes from the latest tag.

**On your PC (Windows), optional.** Needs internet the first time (Gradle downloads JDK 25, Minecraft 1.7.10, Forge and GTNH tooling).
```
gradlew.bat spotlessApply      # auto-format; the build fails on formatting otherwise
gradlew.bat build              # jar lands in build\libs\
```
To read or edit the code in IntelliJ IDEA: run `gradlew.bat setupDecompWorkspace`, then open the folder in IntelliJ.

## Test stage 1
1. Download the newest `chestsorter-<commit>.jar` from GitHub Actions (not `-dev` or `-sources`) and replace the old one in the `mods` folder of your **copy** of the GTNH 2.9.0-RC-2 Prism instance.
2. In a throwaway singleplayer world, place within 16 blocks of you: one single chest, one double chest, one trapped chest next to a normal chest, and two or three Iron Chest tiers (include a Dirt Chest 9000 if you have one).
3. Press "Start / stop sorting".

Expected chat:
```
[Chest Sorter] SCAN
[Chest Sorter] Found N containers within 16 blocks.
  Double Chest (54 slots) at x y z + x y z, 2.3 m
  Iron Chest (54 slots) at x y z, 3.1 m
  ...
[Chest Sorter] IDLE
```
Check: the double chest is listed **once** with 54 slots; the trapped chest and the chest beside it are listed **separately** with 27 slots each; each Iron Chest shows its tier name and slot count (Iron 54, Gold 81, Diamond 108); the Dirt Chest is **not** listed; nothing beyond 16 blocks is listed.

The full list also goes to `logs/latest.log` (search "Chest Sorter" or "chestsorter"). The radius is `scanRadius` in `config/chestsorter.cfg` (1–64).

## Layout
| Path | What |
|---|---|
| `src/main/java/.../chestsorter/core/` | Pure Java logic, no Minecraft imports. Checked by `tools/check-core.sh`. |
| `src/main/java/.../chestsorter/client/` | Client code: keybinds, tick/key handlers. |
| `docs/research-notes.md` | Findings and decisions, tagged by confidence. |
| `docs/data/` | RC-2 manifest, mod list, container scan results. |
| `tools/test-core.sh` | Unit tests for the core package (runs without Minecraft). |
| `tools/check-stubs.sh` | Typo check against hand-verified 1.7.10 API stubs (not a real build). |
| `tools/scan_containers.py` | Scans mod jars for inventory tile entities. |

## Servers
Built for singleplayer, your own server, and public servers. Many public servers ban automated movement and inventory clicking; check each server's rules before turning it on there.

## License
LGPL-3.0 (`LICENSE`), which builds on the GPL-3.0 (`COPYING`).
