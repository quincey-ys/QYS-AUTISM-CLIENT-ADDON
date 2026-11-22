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
- Recognizes supported math questions, Minecraft word scrambles, reversed-text prompts, typing prompts, higher/lower number guessing, and a built-in set of Minecraft trivia.
- Configurable answer delay and minimum interval between answers.
- Suggestion-only mode displays the answer in the action bar; Auto Send is a separate setting and is off by default.
- Disabled by default; settings are stored in config/qys-chat-game-guesser.properties.

### Auto TNT Cart
- Watches your own rail placement and supports vanilla and modded rail blocks through the shared rail-block base class.
- Places a TNT Minecart on the detected rail, switches to a hotbar Bow, draws it for the configured charge duration, releases, and restores the original hotbar slot.
- Single mode runs once and disarms; Repeat mode runs once for each new rail placement; Manual Assist places the cart and selects the Bow without firing.
- Configurable rail-to-cart delay, cart-to-bow delay, bow charge duration, and repeat cooldown.
- Requires a TNT Minecart and a Bow in the hotbar. A Flame-enchanted Bow is recommended for ignition.
- Disabled by default. Server rules may prohibit automated item-use sequences; use only where permitted.

### Crystal / Anchor
- Breaks only an End Crystal spawned at a block you just used with an End Crystal.
- Uses a delayed queue for crystals you placed yourself and never rotates your view.
- Crystal controls: require right-click, post-spawn break delay, maximum breaks per second, and placement matching window.
- Anchor combo runs as timed stages: place delay, switch to Glowstone, charge, restore the anchor slot, wait, then detonate.
- Anchor controls: require right-click at detonation, placement/switch/charge/detonation delays, and cooldown between combos.
- Anchor combo is skipped in the Nether. Timing controls are not an anti-cheat bypass; server rules still apply.

## Shared settings screen

Press O to open the unified QYS-UTILS settings screen. It has eight tabs: AutoTotem, Mace Hitter, Combat, Crystal / Anchor, Movement, Chat Games, TNT Cart, and Keybinds. The panel uses compact text, draggable slider controls, and per-tab scrolling. The screen uses a bundled anti-aliased Lato font so it does not depend on fonts installed on your computer. Changes save immediately and again when the screen closes. Crystal / Anchor settings are split into own-crystal and own-anchor columns; changes are saved to qys-auto-place-combat.properties.

## Keybinds
The KEYBINDS tab lets you reassign every QYS-UTILS module toggle and action using a keyboard key or mouse button. Mouse buttons 4 and 5 are supported. Click a binding, press the new key/button, or press Escape to clear it. Duplicate assignments are flagged, and bindings are saved in config/qys-utils-keybinds.properties.

Default bindings: O opens the menu; T toggles AutoTotem; R toggles Mace Hitter; G triggers Slam Now; K toggles Aim Assist; L toggles Trigger Bot; J toggles own-crystal breaking; H toggles the Anchor Combo; P toggles Auto Sprint; U toggles Chat Game Guesser; I toggles Auto TNT Cart; Y activates Auto TNT Cart on the targeted rail.

## Configuration files
- config/autism-autototem.properties
- config/qys-mace-hitter.properties
- config/qys-auto-place-combat.properties (Crystal and Anchor delays/rate limits)
- config/qys-auto-sprint.properties (Auto Sprint settings)
- config/qys-combat-assist.properties (Aim Assist and Trigger Bot settings)
- config/qys-chat-game-guesser.properties (Chat Game Guesser settings)
- config/qys-auto-tnt-cart.properties (Auto TNT Cart timing and mode)
- config/qys-utils-keybinds.properties (all QYS-UTILS keyboard and mouse bindings)

The AutoTotem config filename and property names are kept compatible with the original QYS AutoTotem config.

## Requirements
- Minecraft Java Edition 26.3
- Fabric Loader 0.20.1 or newer
- Fabric API for 26.3
- Java 25

## Build
Run Gradle 9.8.2 with Java 25 and execute gradle build. The GitHub Actions workflow prepares the bundled Lato font and packages its complete license/attribution notice into the JAR. The distributable mod is written to build/libs/QYS-UTILS.jar.
