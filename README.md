# QYS Mace Hitter

A standalone Fabric client mod for Minecraft 26.3. It is **not an AUTISM Client addon** and has no AUTISM Client dependency.

## Features

- Nearest-target selection with configurable range.
- Player-only or all-living-entity targeting.
- Optional line-of-sight and teammate checks.
- Automatic hotbar mace selection.
- Automatic jump assistance for falling mace hits.
- Optional falling-only attacks.
- Shield-blocker priority attacks.
- Configurable tick delay.
- Manual **Slam Now** action.

## Keybinds

| Key | Action |
|---|---|
| R | Toggle Mace Hitter |
| G | Slam Now |

Change these in **Options → Controls → Key Binds**.

## Configuration

The mod creates `config/qys-mace-hitter.properties` on first launch. Close Minecraft before editing it. Changes are loaded on the next launch.

| Property | Default | Meaning |
|---|---:|---|
| enabled | false | Start enabled |
| range | 3.2 | Target range (1.0–6.0) |
| delayTicks | 0 | Delay between attacks (0–20) |
| rotate | true | Rotate toward target |
| autoSwap | true | Select a mace from the hotbar |
| autoJump | true | Jump automatically |
| fallingOnly | false | Attack only while falling, except shield-stun priority |
| shieldStun | true | Prioritize shield blockers |
| requireMace | false | Do not attack unless a mace is available |
| ignoreWalls | false | Ignore line of sight |
| playersOnly | true | Only target players |
| ignoreTeammates | true | Skip allied players |

## Requirements

- Minecraft Java Edition 26.3
- Fabric Loader 0.20.1 or newer
- Fabric API for 26.3
- Java 25

## Build

Run `gradle build` with Gradle 9.8.2 and Java 25. The JAR is written to `build/libs/`.
