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

### Auto Sprint
- Automatically starts vanilla sprint while moving forward.
- Stops forcing sprint when you stop, move backward, sneak, open a screen, or use an item when the item-use pause is enabled.
- Does not change movement speed or send custom movement packets.
- Disabled by default; settings are saved in config/qys-auto-sprint.properties.

### Combat Assist
- Separate Aim Assist and Trigger Bot toggles.
- Aim Assist supports configurable player, hostile-mob, and passive-mob targets, range, FOV, strength, line-of-sight checks, and teammate filtering.
- Aim Assist defaults to requiring the attack key to be held.
- Trigger Bot only attacks a valid entity already under the crosshair and supports independent target filters, weapon-only mode, and attack delay.
- Both combat assists are disabled by default and use vanilla player attack/rotation APIs.

### Mace Hitter
- Nearest-target selection with configurable range.
- Player-only or all-living-entity targeting.
- Optional line-of-sight and teammate checks.
- Automatic hotbar mace selection and jump assistance.
- Optional falling-only attacks and shield-stun priority.
- Configurable attack delay and manual Slam Now action.

### Chat Game Guesser
- Recognizes supported math questions, Minecraft word scrambles, reversed-text prompts, typing prompts, and a built-in set of Minecraft trivia.
- Configurable answer delay and minimum interval between answers.
- Suggestion-only mode displays the answer in the action bar; Auto Send is a separate setting and is off by default.
- Disabled by default; settings are stored in config/qys-chat-game-guesser.properties.

### Crystal / Anchor
- Breaks only an End Crystal spawned at a block you just used with an End Crystal.
- Uses a delayed queue for crystals you placed yourself and never rotates your view.
- Crystal controls: require right-click, post-spawn break delay, maximum breaks per second, and placement matching window.
- Anchor combo runs as timed stages: place delay, switch to Glowstone, charge, restore the anchor slot, wait, then detonate.
- Anchor controls: require right-click at detonation, placement/switch/charge/detonation delays, and cooldown between combos.
- Anchor combo is skipped in the Nether. Timing controls are not an anti-cheat bypass; server rules still apply.

## Shared settings screen

Press O to open the unified QYS-UTILS settings screen. It has six tabs: AutoTotem, Mace Hitter, Combat, Crystal / Anchor, Movement, and Chat Games. The panel uses compact text, draggable slider controls, and per-tab scrolling. The screen uses a bundled anti-aliased Lato font so it does not depend on fonts installed on your computer. Changes save immediately and again when the screen closes. Crystal / Anchor settings are split into own-crystal and own-anchor columns; changes are saved to qys-auto-place-combat.properties.

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
- config/qys-auto-sprint.properties (Auto Sprint settings)
- config/qys-combat-assist.properties (Aim Assist and Trigger Bot settings)
- config/qys-chat-game-guesser.properties (Chat Game Guesser settings)

The AutoTotem config filename and property names are kept compatible with the original QYS AutoTotem config.

## Requirements
- Minecraft Java Edition 26.3
- Fabric Loader 0.20.1 or newer
- Fabric API for 26.3
- Java 25

## Build
Run Gradle 9.8.2 with Java 25 and execute gradle build. The GitHub Actions workflow prepares the bundled Lato font and packages its complete license/attribution notice into the JAR. The distributable mod is written to build/libs/QYS-UTILS.jar.
