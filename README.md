# Combat Toggle Mod

A Minecraft Forge mod that adds a PvP toggle system with combat tagging, cooldowns, and admin controls.

## Version Information

- **Mod Version:** 1.0.0
- **Minecraft Version:** 1.20.1
- **Forge Version:** 47.3.0
- **Java Version:** 17

## Features

### Core Functionality
- **Peace/Combat Mode Toggle:** Players can switch between Peace mode (PvP disabled) and Combat mode (PvP enabled)
- **Keybind:** Caps Lock key to toggle modes (configurable)
- **HUD Display:** Visual indicator showing current mode (top center of screen, 120x27 pixels)
- **Nameplate Indicators:**
  - Text/emoji prefixes: [PVP] for Combat mode, [PEACE] for Peace mode (fully customizable)
  - Color coding: RED nameplate for Combat mode, BLUE for Peace mode (vanilla scoreboard teams)
  - Both prefixes and colors can be independently enabled/disabled
- **Combat Tagging:** Players are tagged for 30 seconds after PvP engagement
- **Smart Cooldown System:** PvP-triggered 10-minute cooldown (not toggle-triggered by default)

### Combat Logging Protection
- Players cannot toggle to Peace mode while combat-tagged (configurable)
- Combat tags persist across logout/login and server restarts
- Optional forced Combat mode while tagged
- Tag data stored in player persistent NBT

### Admin Commands
All commands require permission level 2 (OP):

```
/combattoggle get <player>
  - View a player's current mode and tag status

/combattoggle set <player> <peace|combat> [bypassCooldown]
  - Set a player's mode (optionally bypass cooldown)

/combattoggle resetcooldown <player>
  - Reset a player's toggle cooldown

/combattoggle tag <player> [seconds]
  - Apply combat tag to a player (default: config value)

/combattoggle untag <player>
  - Remove combat tag from a player

/combattoggle reload
  - Reload configuration (ceremonial - Forge auto-reloads)
```

## Configuration

Configuration file: `config/combattoggle.toml`

### Server Settings

**Combat & Tagging:**
- `combatTagSeconds` (default: 30) - Combat tag duration after PvP
- `requireBothCombatEnabled` (default: true) - If true, both players must be in Combat mode for PvP
- `forceCombatWhileTagged` (default: true) - Force Combat mode while tagged
- `allowToggleWhileTagged` (default: false) - Allow toggling while combat-tagged

**Cooldown System (NEW):**
- `cooldownSeconds` (default: 600) - Cooldown duration in seconds (10 minutes)
- `cooldownTriggersOnToggle` (default: false) - If true, cooldown starts on toggle; if false, toggling is instant
- `cooldownTriggersOnPvp` (default: true) - If true, cooldown starts when dealing/receiving PvP damage
- `cooldownAppliesToPeaceOnly` (default: true) - If true, cooldown only blocks Peace mode (Combat always available)

**Admin:**
- `allowAdminBypassCooldown` (default: true) - Allow admins to bypass cooldown via commands

**Nameplate Visual Customization:**
- `useEmojiPrefixes` (default: true) - Show text/emoji prefixes on nameplates
- `combatEmoji` (default: "[PVP] ") - Text/emoji prefix for Combat mode players
- `peaceEmoji` (default: "[PEACE] ") - Text/emoji prefix for Peace mode players
- `useNameplateColors` (default: true) - Use scoreboard team colors (RED for Combat, BLUE for Peace)

### Client Settings (Server-Controlled)
- `showHud` (default: true) - Display HUD overlay

## Installation

### For Players
1. Install Minecraft Forge 47.3.0 for Minecraft 1.20.1
2. Download the Combat Toggle mod JAR file
3. Place the JAR in your `mods` folder
4. Launch Minecraft

### For Developers

**Prerequisites:**
- Java Development Kit (JDK) 17
- Gradle 8.x or higher

**Setup:**
1. Clone or download this repository
2. Open a terminal in the project directory
3. Run `gradle wrapper` to generate Gradle wrapper files (if missing)
4. Run `./gradlew build` (Linux/Mac) or `gradlew.bat build` (Windows)
5. The compiled JAR will be in `build/libs/`

**Development Environment:**
- Run `./gradlew genIntellijRuns` for IntelliJ IDEA
- Run `./gradlew genEclipseRuns` for Eclipse
- Run `./gradlew runClient` to test in development

## Project Structure

```
src/main/java/com/runecraft/combattoggle/
├── CombatToggle.java              # Main mod class
├── config/
│   └── CTConfig.java              # Server configuration
├── data/
│   └── CombatToggleData.java      # Player data persistence
├── events/
│   ├── CombatEnforcementEvents.java   # PvP blocking & tagging
│   ├── PlayerLifecycleEvents.java     # Login/respawn sync
│   └── CommandEvents.java             # Admin commands
├── network/
│   ├── PacketHandler.java             # Network registration
│   ├── C2SRequestTogglePacket.java    # Client→Server toggle request
│   └── S2CSyncStatePacket.java        # Server→Client state sync
├── client/
│   ├── ClientCombatState.java         # Client-side state cache
│   ├── ClientKeybinds.java            # Keybind registration
│   └── CombatHudOverlay.java          # HUD rendering
└── util/
    ├── TextUtil.java                  # Text formatting utilities
    └── TeamManager.java               # Scoreboard team management

src/main/resources/
├── META-INF/
│   └── mods.toml                      # Mod metadata
├── pack.mcmeta                        # Resource pack metadata
└── assets/combattoggle/
    ├── lang/
    │   └── en_us.json                 # English translations
    └── textures/gui/
        ├── peace.png                  # Peace mode HUD icon (120x27)
        └── combat.png                 # Combat mode HUD icon (120x27)
```

## Building the Mod

The mod is ready to build with Gradle wrapper included.

### Build Commands

**Windows:**
```bash
.\gradlew.bat build
```

**Linux/Mac:**
```bash
./gradlew build
```

**Output:**
- Compiled JAR: `build/libs/combattoggle-1.0.0.jar`
- Current size: ~32 KB
- Build time: ~25 seconds

### Development Environment

**IntelliJ IDEA:**
```bash
.\gradlew.bat genIntellijRuns
```

**Eclipse:**
```bash
.\gradlew.bat genEclipseRuns
```

**Test Client:**
```bash
.\gradlew.bat runClient
```

## Known Issues

- **IDE Errors:** VSCode/IntelliJ may show false import errors - these are IDE issues and do not affect compilation
- **Scoreboard Conflicts:** May conflict with mods that heavily use scoreboard teams (factions, etc.)

## License

GPL-3.0 License - See project documentation for details

## Credits

- **Developer:** Runecraft
- **Minecraft Version:** 1.20.1
- **Forge Version:** 47.3.0

## Support

For issues, questions, or contributions, please refer to the project repository or contact the development team.
