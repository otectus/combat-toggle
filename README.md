# Combat Toggle

A Minecraft Forge mod that lets players toggle between Peace and Combat mode, controlling who can engage in PvP. Includes combat tagging, configurable cooldowns, nameplate indicators, and a full admin command suite.

## Requirements

- Minecraft 1.20.1
- Forge 47.3.0+
- Java 17

## Installation

1. Install [Minecraft Forge](https://files.minecraftforge.net/) for 1.20.1
2. Drop `combattoggle-1.1.5.jar` into your `mods/` folder
3. Launch the game

## Features

- **Peace/Combat Toggle** — Players press V (rebindable) to switch between Peace mode (PvP off) and Combat mode (PvP on)
- **HUD Indicator** — Compact icon at the top center of the screen shows the player's current mode
- **Combat Tagging** — PvP engagement tags both players for a configurable duration (default 30s), preventing mode switching mid-fight
- **Smart Cooldowns** — Cooldown triggers on PvP activity by default (not on toggle), so players can freely switch modes outside of combat
- **Nameplate Indicators** — Configurable text prefixes (⚔/☕) and color coding (red/blue) via vanilla scoreboard teams
- **Persistent State** — Player mode, combat tags, and cooldowns survive logout, death, and server restarts via NBT storage

## Admin Commands

All admin commands require OP level 2. `/combattoggle status` is available to all players.

| Command | Description |
|---------|-------------|
| `/combattoggle status` | View your own mode, tag, and cooldown status (all players) |
| `/combattoggle get <player>` | View a player's mode and tag status |
| `/combattoggle set <player> <peace\|combat> [bypassCooldown]` | Force-set a player's mode |
| `/combattoggle resetcooldown <player>` | Clear a player's cooldown (both toggle and PvP) |
| `/combattoggle tag <player> [seconds]` | Apply a combat tag |
| `/combattoggle untag <player>` | Remove a combat tag |
| `/combattoggle reload` | Request config reload |

## Configuration

Server settings are in `<world>/serverconfig/combattoggle-server.toml` (per-world).
Client settings are in `config/combattoggle-client.toml` (global, per-client).

### Combat & Tagging

| Setting | Default | Description |
|---------|---------|-------------|
| `combatTagSeconds` | 30 | Combat tag duration after PvP (seconds) |
| `requireBothCombatEnabled` | true | Both players must be in Combat mode for PvP to occur |
| `forceCombatWhileTagged` | true | Force Combat mode while tagged |
| `allowToggleWhileTagged` | false | Allow toggling while combat-tagged |

### Cooldown System

| Setting | Default | Description |
|---------|---------|-------------|
| `cooldownSeconds` | 600 | Cooldown duration (seconds) |
| `cooldownTriggersOnToggle` | false | Start cooldown on mode toggle |
| `cooldownTriggersOnPvp` | true | Start cooldown on PvP damage |
| `cooldownAppliesToPeaceOnly` | true | Only block toggling to Peace (Combat always allowed) |
| `allowAdminBypassCooldown` | true | Admins can bypass cooldown via commands |

### Nameplate Customization

| Setting | Default | Description |
|---------|---------|-------------|
| `useEmojiPrefixes` | true | Show text prefixes on nameplates |
| `combatEmoji` | `⚔ ` | Prefix for Combat mode players |
| `peaceEmoji` | `☕ ` | Prefix for Peace mode players |
| `useNameplateColors` | true | Color nameplates red (Combat) / blue (Peace) |

### Client

| Setting | Default | Description |
|---------|---------|-------------|
| `showHud` | true | Display the HUD mode indicator |

## Building from Source

```bash
# Clone the repository
git clone https://github.com/otectus/combat-toggle.git
cd combat-toggle

# Build
./gradlew build          # Linux/Mac
.\gradlew.bat build      # Windows

# Output: build/libs/combattoggle-1.1.5.jar
```

### Development

```bash
./gradlew genIntellijRuns    # IntelliJ IDEA
./gradlew genEclipseRuns     # Eclipse
./gradlew runClient          # Test client
```

## Mod Integration

Other mods can query Combat Toggle state via the public API:

```java
import com.runecraft.combattoggle.api.CombatToggleAPI;

// Check player state
boolean inCombat = CombatToggleAPI.isInCombatMode(player);
boolean tagged = CombatToggleAPI.isCombatTagged(player);
boolean pvpAllowed = CombatToggleAPI.isPvpAllowed(attacker, victim);
long cooldownMs = CombatToggleAPI.getCooldownRemainingMs(player);
```

## Project Structure

```
src/main/java/com/runecraft/combattoggle/
├── CombatToggle.java                    # Mod entry point
├── api/CombatToggleAPI.java             # Public API for other mods
├── config/CTConfig.java                 # Configuration definitions (server + client)
├── data/CombatToggleData.java           # Player data persistence (NBT)
├── events/
│   ├── CombatEnforcementEvents.java     # PvP blocking & combat tagging
│   ├── CombatTagTickHandler.java        # Tag expiration notifications
│   ├── PlayerLifecycleEvents.java       # Login/respawn/logout state sync
│   └── CommandEvents.java              # Admin + player commands
├── network/
│   ├── PacketHandler.java               # Network channel setup
│   ├── C2SRequestTogglePacket.java      # Client-to-server toggle request
│   └── S2CSyncStatePacket.java          # Server-to-client state sync
├── client/
│   ├── ClientCombatState.java           # Client-side state cache
│   ├── ClientKeybinds.java              # Keybind registration
│   └── CombatHudOverlay.java            # HUD rendering
└── util/
    ├── TextUtil.java                    # Chat message formatting
    └── TeamManager.java                 # Scoreboard team management
```

## Known Issues

- IDEs may show false import errors that do not affect compilation
- May conflict with mods that heavily manage scoreboard teams (factions mods, etc.)

## License

[GPL-3.0](LICENSE)

## Credits

Developed by Runecraft
