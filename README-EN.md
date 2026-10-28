# Building Album

A **Fabric 1.20.1** mod that adds a new **“Structure Blueprint”** tab to the Creative Mode inventory, containing multiple building blueprints.

When holding a blueprint and aiming at the ground or a block face, the mod displays a semi-transparent preview of the entire structure. **Right-click to place the actual building** into the world.

> Compatibility preserved: when holding a normal block, the vanilla-style single-block placement preview is still displayed with a cyan outline and a semi-transparent ghost block. Press `G` to toggle all previews on or off, and press `R` to rotate the blueprint preview by 90°.

## Behavior

* Blueprint items are displayed in order inside the **“Structure Blueprint”** Creative Mode tab. Each blueprint corresponds to a structure JSON file.

* When holding a blueprint and aiming at a block surface, the entire building is shown as a preview:

  * Blocks are rendered semi-transparently.
  * A cyan outline is drawn around the blocks. If the preview would overwrite existing non-air blocks, the outline turns red and the action bar reports how many blocks would be overwritten (only when the count changes, with a short cooldown so it does not spam every tick).
  * The structure rotates in 90° increments according to the player's facing direction.
  * By default, the structure's **front side** — the `+Z` side in the JSON file — faces toward the player.

* The preview and actual placement use the exact same coordinate transformation logic. Wherever the preview appears is exactly where the structure will be placed after right-clicking.

* Press `R` (`Controls → Building Album`, rebindable) to add a 90° rotation on top of the facing-based rotation. Each press advances 0° → 90° → 180° → 270° and wraps around. The offset is stored per player on the server and synced to the client, so the preview and the placed structure always agree. It resets when the player disconnects.

* Building placement is currently **free**:

  * No materials are consumed.
  * The player's inventory is not checked.
  * Placement is executed server-side.
  * Existing blocks in the target area may be overwritten directly.
  * A placement sound plays on success, and a few particles (at most 24 spots) are scattered over the finished build.

* When holding a normal `BlockItem`, the existing single-block ghost placement preview remains unchanged.

## Undoing a Build

The previous state of every overwritten block is recorded, so builds can be reverted:

```text
/buildingalbum undo          # undo the most recent build
/buildingalbum undo 3        # undo the last three builds
```

* Each player keeps the last **8** builds, restored newest-first.
* History lives in server memory only and is cleared when the server restarts.
* Only block states are restored; container contents, sign text and other block-entity data are not.
* Undo overwrites whatever is currently at those positions, including changes made after the build.
* Players can only undo their own builds. Records for dimensions that no longer exist are skipped.

## Listing Blueprints

```text
/buildingalbum list
```

Lists every registered blueprint (built-in and user) sorted by structure id, together with its size (X × Y × Z). Available to both players and the console.

## Where Blueprints Come From

When the mod starts, blueprints are registered in the following order:

1. **Built-in blueprints** packaged with the mod:

   * Small House
   * Watchtower
   * Altar
   * Fountain
   * Obelisk
   * Gazebo
   * Campsite

2. **User blueprints** loaded by scanning:

   `building_album/blueprints/*.json`

   inside the Minecraft configuration directory.

   Development environment:

   `run/config/building_album/blueprints/`

   Normal Minecraft installation:

   `.minecraft/config/building_album/blueprints/`

   **Simply place a JSON file into this folder and it will automatically be registered as a blueprint and added to the Creative Mode tab. No code changes are required.**

### User Blueprint JSON Format

```json
{
  "size": {"x": 5, "y": 4, "z": 5},
  "blocks": [
    {"x": 0, "y": 0, "z": 0, "block": "minecraft:oak_planks"},
    {"x": 0, "y": 1, "z": 0, "block": "minecraft:oak_log", "properties": {"axis": "y"}},
    {"x": 2, "y": 1, "z": 4, "block": "minecraft:glass"}
  ]
}
```

* Coordinates are relative coordinates, with `0,0,0` representing the minimum corner of the structure.

  Valid coordinate ranges are:

  * `x ∈ [0, size.x)`
  * `y ∈ [0, size.y)`
  * `z ∈ [0, size.z)`

* `block` is the registered block ID.

  Any currently loaded block is supported, including blocks added by other mods.

* `properties` is optional and can be used to specify block state properties such as:

  * `axis`
  * `facing`

* The blueprint item ID is generated as:

  `building_album:blueprint_<filename>`

  The filename is normalized so that only lowercase letters, numbers, `_`, `-`, and `.` are preserved.

* The display name is generated from a beautified version of the filename.

  For example:

  `my_house.json` → `My House`

* After adding or modifying blueprint files, **restart the game** for the changes to take effect, because Minecraft registries are constructed during startup.

* Built-in structure files are also stored inside the mod JAR under:

  `assets/building_album/blueprints/`

  These files can be used as reference examples when creating custom blueprints.

### Saving a Build from the World

You do not have to write JSON by hand: select a region in game and export it (`save` requires operator permission, which single-player Creative satisfies by default):

```text
/buildingalbum pos1     # set the first corner to the block you are looking at (max 64 blocks away)
/buildingalbum pos2     # set the second corner the same way
/buildingalbum save my_house
```

* Both corners must be in the same dimension; changing dimensions clears the selection.
* The file is written to `config/building_album/blueprints/<name>.json` in exactly the manual blueprint format.
* **Restart the game/server** and the new blueprint appears in the Creative tab (item id `building_album:blueprint_<name>`).
* Only non-air blocks are saved, up to **32768** blocks (32×32×32) per capture. Air cells are skipped, so blueprints are additive: placing one does not clear existing blocks or air at the target.
* Only block states are captured. Container contents, sign text and other block-entity data are not saved.
* Existing files and existing blueprint names are never overwritten; delete the old file manually if you want to replace it.
* On a dedicated server the file lands in the server's config directory; clients also need the same file to see and use that blueprint.

## Configuration

On first launch, the mod generates:

`.minecraft/config/building-album.json`

```json
{
  "enabled": true,
  "opacity": 0.38,
  "edgeOpacity": 0.85
}
```

* `opacity` controls the transparency of preview blocks.

  The value is automatically clamped to:

  `0.05`–`0.80`

* `edgeOpacity` controls the transparency of the cyan outline.

  The value is automatically clamped to:

  `0.10`–`1.00`

After manually editing the configuration file, restart the game for the changes to take effect.

## Building and Running

Requires **JDK 17–21**.

Do **not** use JDK 22 or newer. Fabric `0.16.14` uses a version of Mixin that cannot parse class files produced by newer JDK versions, which may cause the game to crash during startup.

The project uses `org.gradle.java.home` inside `gradle.properties` to point to the repository-local:

`.tools/jdk-21`

This is a Temurin JDK 21 installation that includes `javac` and is excluded from Git through `.gitignore`.

The project uses Gradle `9.5.1`.

`fabric-loom 1.10.5` requires Gradle `8.12` or newer.

```powershell
.\gradlew.bat build      # Compile and build the JAR
                         # Output: build/libs/building-album-1.0.0.jar

.\gradlew.bat runClient  # Launch the development Minecraft client
```

## Installation

1. Install the Fabric Loader version compatible with Minecraft `1.20.1`.

2. Download the Fabric API version compatible with Minecraft `1.20.1`.

3. Place both the Fabric API JAR and this mod's JAR into:

   `.minecraft/mods/`

4. Launch Minecraft using a Fabric `1.20.1` profile.

The mod currently uses **client-and-server registration**, so it must also be installed on the server. Otherwise, blueprint items cannot be registered correctly.

The Creative Mode blueprint tab, structure previews, and right-click building placement are supported in:

* Singleplayer
* LAN worlds
* Multiplayer servers
