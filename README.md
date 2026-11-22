# QYS-UTILS

Standalone Fabric client utility mod for Minecraft Java 26.3. This is one independent mod JAR, not an AUTISM Client addon.

## Included modules

### AutoTotem
- Smart, Switch, and PickUp modes.
- Health threshold and projected fall-damage triggers.
- Missing-armor trigger.
- Safety threshold, safe switch-back delay, and blast-resistant-hole check.
- Optional Elytra ignore.
- Existing AutoTotem settings are retained in config/autism-autototem.properties.

### Mace Hitter
- Nearest-target selection with configurable range.
- Player-only or all-living-entity targeting.
- Optional line-of-sight and teammate checks.
- Automatic hotbar mace selection and jump assistance.
- Optional falling-only attacks and shield-stun priority.
- Configurable attack delay and manual Slam Now action.

### Crystal / Anchor
- Breaks only an End Crystal spawned at a block you just used with an End Crystal.
- Uses a delayed queue for crystals you placed yourself and never rotates your view.
- Crystal controls: require right-click, post-spawn break delay, maximum breaks per second, and placement matching window.
- Anchor combo runs as timed stages: place delay, switch to Glowstone, charge, restore the anchor slot, wait, then detonate.
- Anchor controls: require right-click at detonation, placement/switch/charge/detonation delays, and cooldown between combos.
- Anchor combo is skipped in the Nether. Timing controls are not an anti-cheat bypass; server rules still apply.

## Shared settings screen

Press O to open the unified QYS-UTILS settings screen. It contains AutoTotem, Mace Hitter, and Crystal / Anchor tabs, opaque panels with squircle corners, and settings controls. The screen uses a bundled anti-aliased Lato font so it does not depend on fonts installed on your computer. Changes save immediately and again when the screen closes. Crystal / Anchor settings are split into own-crystal and own-anchor columns; changes are saved to qys-auto-place-combat.properties.

## Keybinds
- T: Toggle AutoTotem
- O: Open QYS-UTILS settings
- R: Toggle Mace Hitter
- G: Slam Now

All keybinds can be changed under Options > Controls > Key Binds.

## Configuration files
- config/autism-autototem.properties
- config/qys-mace-hitter.properties
- config/qys-auto-place-combat.properties (Crystal and Anchor delays/rate limits)

The AutoTotem config filename and property names are kept compatible with the original QYS AutoTotem config.

## Requirements
- Minecraft Java Edition 26.3
- Fabric Loader 0.20.1 or newer
- Fabric API for 26.3
- Java 25

## Build
Run Gradle 9.8.2 with Java 25 and execute gradle build. The GitHub Actions workflow prepares the bundled Lato font and packages its complete license/attribution notice into the JAR. The distributable mod is written to build/libs/QYS-UTILS.jar.
