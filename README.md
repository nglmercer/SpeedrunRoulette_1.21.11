# Speedrun Roulette

A Minecraft NeoForge mod that adds randomized speedrun objectives with a spinning wheel, timer, HUD, and extensive customization.

- **Minecraft:** 1.21.11
- **Loader:** NeoForge 21.11.13+
- **Java:** 21
- **Author:** asus

---

## Features

### Spinning Wheel
Press `R` to spin an animated slot machine wheel and receive random objectives. Columns spin sequentially with sound effects and display the selected goals with adaptive layout.

### Timer System
- Nanosecond precision timer
- Auto-starts when objectives are set
- Manual pause with `P`
- Auto-pauses when Minecraft is paused (singleplayer menus)

### HUD (Press `H` to cycle modes)
- **Full** — Timer, objectives list with completion status, item icons, and stats
- **Minimal** — Timer only (large display)
- **Hidden** — Nothing displayed

Stats tracked: Deaths, Distance traveled (meters), Days played.

### Objective Types
- **Items** — All obtainable items (potions, music discs, etc.)
- **Blocks** — Placeable block items
- **Advancements** — Minecraft's built-in advancements

### Victory Screen
Shows when all objectives are completed:
- Final time and statistics
- Split milestones (Nether, End, Village, Fortress, Bastion, Stronghold)
- Buttons: Play Again, New Run, Main Menu, Stay in Game

### Run History
Victory (★) and defeat (☠) icons appear on the world selection screen with hover details.

---

## Keybindings

| Key | Action |
|-----|--------|
| `R` | Open wheel (or reminder if objectives active) |
| `P` | Pause/Resume timer |
| `H` | Cycle HUD mode |

---

## Commands

| Command | Description |
|---------|-------------|
| `/speedrun wheel` | Open the wheel |
| `/speedrun reminder` | Show current objectives |
| `/speedrun new` | Start new run with new objectives |
| `/speedrun retry` | Retry with same objectives |
| `/speedrun giveup` | Abandon current run |
| `/speedrun reset` | Reset and create new world |
| `/speedrun pause` | Toggle timer pause |
| `/speedrun hud` | Cycle HUD mode |
| `/speedrun config` | Open configuration |
| `/speedrun status` | Show status in chat |

---

## Configuration

Access via the "Speedrun Config" button on the title screen or `/speedrun config`.

### General
| Option | Default | Description |
|--------|---------|-------------|
| Auto-open Wheel | `true` | Open wheel when joining a world with no objectives |
| Auto-start Timer | `true` | Start timer automatically when objectives are set |
| Objective Count | `1` | Number of objectives per run (1–10) |
| Forced Language | _(empty)_ | Override mod language |

### Pool Settings
Enable/disable items, blocks, and advancements. Use **Customize Pool** to blacklist specific objectives with search, type, and dimension filters.

### HUD Settings
Configure timer scale, item icon scale, text scale, text color, and timer color.

### Supported Languages
English, French, German, Spanish, Italian, Portuguese (Brazil), Russian, Chinese (Simplified)

---

## Installation

1. Install [NeoForge](https://neoforged.net/) for Minecraft 1.21.11
2. Download the latest `.jar` from [Releases](../../releases)
3. Place the `.jar` in your `mods` folder

---

## Building

```bash
git clone https://github.com/your-repo/SpeedrunRoulette_1.21.11.git
cd SpeedrunRoulette_1.21.11
./gradlew build
```

Build output: `build/libs/`

---

## License

All Rights Reserved

Minecraft mapping names used under the [Mojang license](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md).

---

## Credits

- [NeoForge Documentation](https://docs.neoforged.net/)
- [NeoForged Discord](https://discord.neoforged.net/)
