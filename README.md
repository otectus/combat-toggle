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
  - Text/emoji prefixes: [PVP] for Combat mode, [PEACE] for Peace mode (fully customizable - see EMOJI_GUIDE.md)
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
- `combatEmoji` (default: "[PVP] ") - Text/emoji prefix for Combat mode players (see EMOJI_GUIDE.md for emoji options)
- `peaceEmoji` (default: "[PEACE] ") - Text/emoji prefix for Peace mode players (see EMOJI_GUIDE.md for emoji options)
- `useNameplateColors` (default: true) - Use scoreboard team colors (RED for Combat, BLUE for Peace)

### Client Settings (Server-Controlled)
- `showHud` (default: true) - Display HUD overlay
- `allowClientButtonClick` (default: true) - Allow clicking HUD to toggle (not yet implemented)

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
    └── TextUtil.java                  # Text formatting utilities

src/main/resources/
├── META-INF/
│   └── mods.toml                      # Mod metadata
├── pack.mcmeta                        # Resource pack metadata
└── assets/combattoggle/
    ├── lang/
    │   └── en_us.json                 # English translations
    └── textures/gui/
        ├── peace.png                  # Peace mode icon (450x101)
        └── combat.png                 # Combat mode icon (450x101)
```

## What's New in Latest Build

### ✅ Build 7 - PvP-Triggered Cooldown System
- **Smart Cooldown:** Cooldown now triggers on PvP activity, not just toggling
- **Free Safe Zone Toggling:** Players can switch modes instantly when not fighting
- **Configurable Behavior:** Three new config options for fine-tuned control
- **Better UX:** Clear messages about why cooldown is active
- **See:** `PVP_COOLDOWN_SYSTEM.md` for full documentation

### ✅ Build 6 - Nameplate Color System
- **Visual Status:** RED nameplates for Combat mode, BLUE for Peace mode
- **Vanilla Teams:** Uses Minecraft's scoreboard system (no client hacks)
- **Auto-Sync:** Updates on toggle, login, respawn, and admin commands
- **See:** `NAMEPLATE_COLORS.md` for full documentation

### ✅ Build 5 - HUD Rendering Fix
- **Proper Scaling:** HUD icons now display at correct 120x27 pixel size
- **Matrix Transformation:** Uses pose stack scaling for proper rendering
- **Full Texture:** Shows complete icon, not just corner/enlarged portion

### ✅ Build 4 - Startup Crash Fix
- **Fixed mods.toml:** Corrected TOML format for Forge 1.20.1
- **Proper Syntax:** Changed to `[[mods]]` array with required fields
- **See:** `CRASH_FIX.md` for technical details

## Current Status

### ✅ Fully Implemented
- ✅ All core Java classes
- ✅ Server-side logic (config, data persistence, enforcement)
- ✅ Client-side logic (state management, keybinds, HUD)
- ✅ Network packet system
- ✅ Admin command system
- ✅ PvP-triggered cooldown system
- ✅ Nameplate color system (scoreboard teams)
- ✅ HUD rendering with proper scaling
- ✅ Configuration files
- ✅ Language localization
- ✅ Texture assets (peace.png, combat.png)
- ✅ **Build successful** - JAR ready for deployment

### 🧪 Testing Needed
- [ ] In-game mod loading verification
- [ ] HUD display at 120x27 pixels
- [ ] Nameplate colors (RED/BLUE)
- [ ] PvP-triggered cooldown behavior
- [ ] All admin commands
- [ ] Combat tag persistence

### 🔧 Optional Future Enhancements
- Click-to-toggle HUD functionality
- Sound effects for mode changes
- Particle effects for combat tagging
- Gradual cooldown reduction
- Safe zone detection
- Per-world cooldowns

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
- Current size: ~47 KB
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

## Testing Checklist

### Basic Functionality
- [ ] Mod loads without crashes
- [ ] Keybind (Caps Lock) toggles mode
- [ ] HUD displays at 120x27 pixels (top center)
- [ ] HUD shows correct icon (peace.png or combat.png)

### Nameplate Colors
- [ ] Combat mode → RED nameplate
- [ ] Peace mode → BLUE nameplate
- [ ] Colors visible in tab list
- [ ] Colors update on toggle
- [ ] Colors persist after relog

### PvP System
- [ ] PvP blocked in Peace mode
- [ ] PvP works in Combat mode
- [ ] Combat tagging applies after PvP (30s default)
- [ ] Combat tag prevents Peace toggle (if configured)
- [ ] Tag persists across logout/login

### Cooldown System
- [ ] Free toggling in safe zones (no recent PvP)
- [ ] PvP triggers 10-minute cooldown
- [ ] Cooldown blocks Peace mode toggle
- [ ] Cooldown allows Combat mode toggle (if `cooldownAppliesToPeaceOnly = true`)
- [ ] Clear messages about cooldown status
- [ ] Cooldown persists across relog

### Admin Commands
- [ ] `/combattoggle get <player>` shows status
- [ ] `/combattoggle set <player> <mode>` works
- [ ] `/combattoggle resetcooldown <player>` clears cooldown
- [ ] `/combattoggle tag <player>` applies tag
- [ ] `/combattoggle untag <player>` removes tag
- [ ] Bypass cooldown with `bypassCooldown` parameter

### Configuration
- [ ] Config file generates on first run
- [ ] Config changes take effect
- [ ] All new cooldown options work correctly

## Documentation

- **README.md** - This file, main documentation
- **PVP_COOLDOWN_SYSTEM.md** - Complete guide to the PvP-triggered cooldown system
- **NAMEPLATE_COLORS.md** - Documentation for the scoreboard team nameplate colors
- **CRASH_FIX.md** - Technical details on fixes applied (mods.toml, HUD rendering)
- **TODO.md** - Development progress tracker
- **BUILD_SUMMARY.md** - Build history and technical notes

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
