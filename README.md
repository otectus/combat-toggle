# Combat Toggle

A Minecraft Forge mod that lets players toggle between Peace and Combat mode, controlling who can engage in PvP. Includes combat tagging, configurable cooldowns, nameplate indicators, a public mod-integration API, and a full admin command suite.

## Requirements

- Minecraft 1.20.1
- Forge 47.3.0+
- Java 17

## Installation

1. Install [Minecraft Forge](https://files.minecraftforge.net/) for 1.20.1
2. Drop `combattoggle-1.2.1.jar` into your `mods/` folder
3. Launch the game

> **Upgrading from 1.1.x?** The 1.2.x line is a breaking release. Network protocol moved from v2 to v3 (clients on 1.1.x will be cleanly rejected at handshake). Player state migrated to a Forge capability with tick-based timers — your players' Combat/Peace mode preserves across the upgrade, but in-flight tags and cooldowns from 1.1.x saves are dropped on first load. Config: `cooldownAppliesToPeaceOnly` was replaced with the `cooldownScope` enum (default `PEACE_ONLY` matches the old `true` setting).

## Features

- **Peace/Combat Toggle** — Players press V (rebindable) to switch between Peace mode (PvP off) and Combat mode (PvP on)
- **HUD Indicator** — Compact icon at the top of the screen shows the player's current mode (position configurable: left/center/right + custom Y offset)
- **Combat Tagging** — PvP engagement tags both players for a configurable duration (default 30s), preventing mode switching mid-fight. Tag deadlines are stored in game ticks, so they survive server restarts correctly.
- **Smart Cooldowns** — Cooldown triggers on PvP activity by default (not on toggle). The `cooldownScope` enum lets admins choose which transition direction the cooldown blocks (`PEACE_ONLY` / `COMBAT_ONLY` / `BOTH` / `NONE`).
- **Nameplate Indicators** — Configurable text prefixes (⚔/☕) and color coding (red/blue) via vanilla scoreboard teams
- **Damage-Type Denylist** — Block specific PvP vectors (e.g. magic, tridents) without disabling PvP entirely
- **Persistent State** — Player mode, combat tags, and cooldowns survive logout, death, and server restarts via Forge capability storage
- **Mod Integration API** — Public `CombatToggleAPI` plus four Forge events (`CombatToggleStateChangeEvent`, `CombatTagAppliedEvent`, `CombatTagExpiredEvent`, `CombatLoggedOutEvent`) for external mods (combat-log penalty mods, safe-zone mods, etc.)

## Player Commands

| Command | Description |
|---------|-------------|
| `/combattoggle status` | View your own mode, tag, and cooldown status |
| `/combattoggle help` | List every subcommand you can run (filtered by your permission level) |

## Admin Commands

All admin commands require OP level 2.

| Command | Description |
|---------|-------------|
| `/combattoggle get <player>` | View a player's mode and tag status |
| `/combattoggle set <player> <combat\|peace> [bypass]` | Force-set a player's mode. Tab-completable; `bypass=true` overrides cooldown and the `forceCombatWhileTagged` guard. |
| `/combattoggle resetcooldown <player>` | Clear a player's cooldown (both toggle and PvP) |
| `/combattoggle tag <player> [seconds]` | Apply a combat tag (1–3600s; defaults to `combatTagSeconds`) |
| `/combattoggle untag <player>` | Remove a combat tag |
| `/combattoggle resync` | Refresh scoreboard teams and HUD state for all online players. (`/combattoggle reload` works as an alias.) |

## Configuration

Server settings are in `<world>/serverconfig/combattoggle-server.toml` (per-world).
Client settings are in `config/combattoggle-client.toml` (global, per-client).

### Combat & Tagging

| Setting | Default | Description |
|---------|---------|-------------|
| `combatTagSeconds` | 30 | Combat tag duration after PvP (seconds) |
| `requireBothCombatEnabled` | true | Both players must be in Combat mode for PvP to occur |
| `forceCombatWhileTagged` | true | Force Combat mode while tagged; admin `/set peace` is refused unless `bypass=true` |
| `allowToggleWhileTagged` | false | Allow toggling while combat-tagged |
| `blockedDamageTypes` | `[]` | List of damage-type resource locations always blocked between players regardless of mode (e.g. `["minecraft:magic", "minecraft:trident"]`) |

### Cooldown System

| Setting | Default | Description |
|---------|---------|-------------|
| `cooldownSeconds` | 600 | Cooldown duration (seconds) |
| `cooldownTriggersOnToggle` | false | Start cooldown on mode toggle |
| `cooldownTriggersOnPvp` | true | Start cooldown on PvP damage |
| `cooldownScope` | `PEACE_ONLY` | Which transition direction the cooldown blocks: `PEACE_ONLY` (Combat→Peace blocked), `COMBAT_ONLY` (Peace→Combat blocked), `BOTH`, or `NONE` (computed but never blocks). |
| `allowAdminBypassCooldown` | true | Admins can bypass cooldown via commands |

### Nameplate Customization

| Setting | Default | Description |
|---------|---------|-------------|
| `useScoreboardTeams` | true | Use vanilla scoreboard teams for nameplates (disable to avoid conflicts with other team-management mods) |
| `useEmojiPrefixes` | true | Show text prefixes on nameplates |
| `combatEmoji` | `⚔ ` | Prefix for Combat mode players |
| `peaceEmoji` | `☕ ` | Prefix for Peace mode players |
| `useNameplateColors` | true | Color nameplates red (Combat) / blue (Peace) |
| `combatTeamName` | `ct_combat` | Scoreboard team name for Combat |
| `peaceTeamName` | `ct_peace` | Scoreboard team name for Peace |

### Client (per-client)

| Setting | Default | Description |
|---------|---------|-------------|
| `showHud` | true | Display the HUD mode indicator |
| `showHudTimers` | true | Display the tag and cooldown countdown timers below the indicator |
| `hudAnchor` | `CENTER` | Horizontal alignment: `LEFT` / `CENTER` / `RIGHT` |
| `hudYOffset` | 6 | Vertical offset from the top of the screen, in pixels |

## Building from Source

```bash
git clone https://github.com/otectus/combat-toggle.git
cd combat-toggle

./gradlew build          # Linux/Mac
.\gradlew.bat build      # Windows

# Output: build/libs/combattoggle-1.2.1.jar
```

### Development

```bash
./gradlew genIntellijRuns    # IntelliJ IDEA
./gradlew genEclipseRuns     # Eclipse
./gradlew runClient          # Test client
```

## Mod Integration

### Querying state

```java
import com.runecraft.combattoggle.api.CombatToggleAPI;
import com.runecraft.combattoggle.data.ToggleDirection;

boolean inCombat        = CombatToggleAPI.isInCombatMode(player);
boolean tagged          = CombatToggleAPI.isCombatTagged(player);
boolean pvpAllowed      = CombatToggleAPI.isPvpAllowed(attacker, victim);
long    tagRemainingMs  = CombatToggleAPI.getCombatTagRemainingMs(player);
long    cooldownMs      = CombatToggleAPI.getCooldownRemainingMs(player);
boolean blockedToPeace  = CombatToggleAPI.isCooldownActive(player, ToggleDirection.TO_PEACE);
```

### Listening to lifecycle events

Subscribe to any of these on the Forge event bus:

| Event | When it fires | Cancelable |
|-------|---------------|------------|
| `CombatToggleStateChangeEvent` | Before a mode change is persisted (player toggle, admin `set`, or force-combat flip). Carries the target state and a `Reason` enum. | Yes — cancel to refuse the transition (e.g. safe-zone mods forcing Peace). |
| `CombatTagAppliedEvent` | When a player is freshly tagged or has an existing tag extended. Carries duration and deadline ticks. | No |
| `CombatTagExpiredEvent` | When a tag elapses, both during normal play and on the player's first login back if it elapsed offline. | No |
| `CombatLoggedOutEvent` | When a player disconnects. Carries remaining tag ticks; `isCombatLog()` returns true if the player disconnected mid-tag. | No |

```java
@SubscribeEvent
public void onTagExpired(CombatTagExpiredEvent e) {
    // e.getEntity() is the Player whose tag just elapsed
}

@SubscribeEvent
public void onCombatLog(CombatLoggedOutEvent e) {
    if (e.isCombatLog()) {
        // Apply your combat-log penalty here
    }
}
```

## Project Structure

```
src/main/java/com/runecraft/combattoggle/
├── CombatToggle.java                       # Mod entry point
├── api/
│   ├── CombatToggleAPI.java                # Public API for other mods (state queries)
│   └── events/
│       ├── CombatToggleStateChangeEvent.java
│       ├── CombatTagAppliedEvent.java
│       ├── CombatTagExpiredEvent.java
│       └── CombatLoggedOutEvent.java
├── config/CTConfig.java                    # Configuration definitions (server + client)
├── data/
│   ├── CombatToggleData.java               # Player state (capability-backed)
│   ├── CombatToggleCapability.java         # Capability registration + attach + clone
│   ├── CooldownScope.java                  # PEACE_ONLY / COMBAT_ONLY / BOTH / NONE
│   └── ToggleDirection.java                # TO_PEACE / TO_COMBAT
├── events/
│   ├── CombatEnforcementEvents.java        # PvP blocking, tagging, force-combat flips
│   ├── CombatTagTickHandler.java           # Tag-expiration notifications (alloc-free)
│   ├── PlayerLifecycleEvents.java          # Login/respawn/logout state sync + legacy migration
│   └── CommandEvents.java                  # Admin + player commands
├── network/
│   ├── PacketHandler.java                  # Network channel setup (protocol v3)
│   ├── C2SRequestTogglePacket.java         # Client-to-server toggle request
│   └── S2CSyncStatePacket.java             # Server-to-client state sync (varint encoded)
├── client/
│   ├── ClientCombatState.java              # Client-side state cache + connect/disconnect reset
│   ├── ClientKeybinds.java                 # Keybind registration (gated by mc.screen == null)
│   └── CombatHudOverlay.java               # HUD rendering (config-driven layout, cached strings)
└── util/
    ├── TextUtil.java                       # Chat message formatting
    └── TeamManager.java                    # Scoreboard team management (cached on config reload)
```

## Known Issues

- May conflict with mods that heavily manage scoreboard teams (factions mods, etc.) — set `useScoreboardTeams=false` to disable Combat Toggle's team usage.
- Cross-server HUD state was previously sticky between disconnect and reconnect; fixed in 1.2.1 by zeroing client state on connect/disconnect.

## License

[GPL-3.0](LICENSE)

## Credits

Developed by Runecraft
