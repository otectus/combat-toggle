# Combat Toggle (1.19.2)

A Minecraft Forge mod that lets players toggle between Peace and Combat mode, controlling who can engage in PvP. Features combat tagging, configurable cooldowns, optional client-side HUD, and **server-installable** deployment — vanilla clients can join servers running this mod and use `/ct` to toggle without installing anything client-side.

## Requirements

- Minecraft 1.19.2
- Forge 43.0.0+
- Java 17

## Installation

1. Install [Minecraft Forge](https://files.minecraftforge.net/) for 1.19.2.
2. Drop `combattoggle-1.2.0.jar` into the `mods/` folder.
3. Launch.

The mod is **server-side optional**: install it on your dedicated server and players can use `/ct` from a vanilla client. Players who also install it client-side get a HUD indicator and a `V` keybind on top.

## Player commands

All four work the same whether you have the mod installed client-side or not.

| Command | Effect |
|---|---|
| `/ct` | Toggle Combat ↔ Peace mode |
| `/combat` | Set yourself to Combat mode |
| `/peace` | Set yourself to Peace mode |
| `/combattoggle status` | View your mode, tag, and cooldown |
| `/combattoggle help` | List every command you can run (filtered by permission) |

## Admin commands

Require OP level 2.

| Command | Effect |
|---|---|
| `/combattoggle get <player>` | View a player's mode and tag status |
| `/combattoggle set <player> <combat\|peace> [bypass]` | Force-set a player's mode. `bypass=true` overrides cooldown + tag guard. |
| `/combattoggle resetcooldown <player>` | Clear both toggle and PvP cooldowns |
| `/combattoggle tag <player> [seconds]` | Apply a combat tag (1–3600s; default = `combatTagSeconds`) |
| `/combattoggle untag <player>` | Remove a combat tag |
| `/combattoggle resync` | Refresh scoreboard teams + sync HUD state for everyone online (`/combattoggle reload` is an alias) |

## HUD (client-side, optional)

When the mod is installed client-side, a small indicator shows the player's current mode. The HUD never affects gameplay — server logic is authoritative.

Configure in `config/combattoggle-client.toml`:

| Key | Default | Notes |
|---|---|---|
| `hudEnabled` | `true` | Master switch |
| `hudShowInCombatMode` | `true` | Display while in Combat |
| `hudShowInPeaceMode` | `true` | Display while in Peace |
| `hudAnchor` | `TOP_CENTER` | One of `TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`, `CENTER_LEFT`, `CENTER`, `CENTER_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT`, `CUSTOM` |
| `hudOffsetX` | `0` | Pixels from anchor (inward). Absolute screen X if `hudAnchor=CUSTOM`. |
| `hudOffsetY` | `6` | Pixels from anchor (inward). Absolute screen Y if `hudAnchor=CUSTOM`. |
| `hudScale` | `1.0` | 0.5 – 3.0 |
| `hudShowTimers` | `true` | Show tag/cooldown countdown text below the icon |

Default keybind is `V`; rebind via Minecraft's Controls menu (Gameplay category).

## Server config

Per-world file: `<world>/serverconfig/combattoggle-server.toml`. All keys carry over verbatim from 1.1.0.

### Combat & tagging

| Key | Default | Notes |
|---|---|---|
| `combatTagSeconds` | `30` | Tag duration after a PvP exchange (0 disables tagging) |
| `requireBothCombatEnabled` | `true` | Both attacker and victim must be in Combat for PvP to land |
| `forceCombatWhileTagged` | `true` | Auto-flip a tagged player to Combat |
| `allowToggleWhileTagged` | `false` | Permit Combat → Peace while tagged |

### Cooldowns

| Key | Default | Notes |
|---|---|---|
| `cooldownSeconds` | `600` | Cooldown duration |
| `cooldownTriggersOnToggle` | `false` | Start cooldown on a manual toggle |
| `cooldownTriggersOnPvp` | `true` | Start cooldown on a PvP exchange |
| `cooldownAppliesToPeaceOnly` | `true` | Cooldown only blocks Combat → Peace; Peace → Combat is always allowed |
| `allowAdminBypassCooldown` | `true` | `/combattoggle set ... bypass=true` works |

### Nameplates

| Key | Default | Notes |
|---|---|---|
| `useScoreboardTeams` | `true` | Disable to avoid conflicts with other team-managing mods |
| `useEmojiPrefixes` | `true` | |
| `combatEmoji` | `⚔ ` | BMP-only Unicode (no surrogate pairs) |
| `peaceEmoji` | `☕ ` | |
| `useNameplateColors` | `true` | RED for Combat, BLUE for Peace |
| `combatTeamName` | `ct_combat` | |
| `peaceTeamName` | `ct_peace` | |

## Server-side-only deployment

The two pieces that make this work:

1. `mods.toml` declares `displayTest = "IGNORE_SERVER_VERSION"`. Vanilla clients see the server as compatible in their multiplayer list (no red ❌).
2. The network channel uses `NetworkRegistry.acceptMissingOr(PROTOCOL::equals)`. Vanilla clients pass the handshake (no "missing channel" kick); modded clients must match the protocol version exactly.

For players without the mod installed client-side: `/ct`, `/combat`, `/peace`, and `/combattoggle status` are all server-handled commands and work identically.

## Upgrading from 1.1.x

This is a breaking release. Most things carry over:

- Server config keys are unchanged — your `combattoggle-server.toml` from 1.1.x continues to work.
- Player mode (Combat / Peace) survives the upgrade — your players keep whatever they were last set to.
- Admin commands still exist, just under the `/combattoggle` namespace.
- HUD keybind is still `V`.

What breaks:

- **In-flight combat tags and cooldowns from 1.1.x are dropped on first 1.2.0 login.** The 1.1.x persisted timers were wall-clock milliseconds with no clock-source guarantee; converting them to the new game-tick clock is unsafe. Mode flag carries; timers reset.
- **The `showHud` server config is dropped.** HUD visibility is now purely client-side via `hudEnabled` in `combattoggle-client.toml`. Operator config files keep the orphan key on first read; Forge logs an unused-key warning. Harmless.
- **Network protocol is `"1"` for the 1.2.x line.** 1.1.x modded clients cannot connect to a 1.2.0 server (handshake rejects); they need to upgrade. Vanilla clients are unaffected.

## Architecture

```
src/main/java/com/runecraft/combattoggle/
├── CombatToggle.java                          # @Mod entry; dedicated-server safe
├── common/                                    # universally loadable (server, integrated, client)
│   ├── CTConfig.java                          # SERVER_SPEC + CLIENT_SPEC
│   ├── HudAnchor.java                         # enum, pure POJO
│   ├── data/
│   │   ├── CombatToggleData.java              # capability state; tick-based timers
│   │   └── CombatToggleCapability.java        # registration + attach + clone
│   └── network/
│       ├── PacketHandler.java                 # SimpleChannel; vanilla-tolerant handshake
│       ├── C2SRequestTogglePacket.java        # client → server (modded keybind path)
│       └── S2CSyncStatePacket.java            # server → client (best-effort)
├── server/                                    # gameplay logic; no client-class refs
│   ├── CommandRegistry.java                   # /ct, /combat, /peace, /combattoggle
│   ├── ToggleService.java                     # central toggle business logic
│   ├── PlayerLifecycleEvents.java             # login/respawn/logout + 1.1.x migration
│   ├── CombatEnforcementEvents.java           # LivingAttackEvent + LivingHurtEvent
│   ├── TeamManager.java                       # scoreboard teams
│   └── TextHelper.java                        # shared chat formatting
└── client/                                    # only loaded on physical client
    ├── ClientCombatState.java                 # cached state + connect/disconnect reset
    ├── ClientKeybinds.java                    # V keybind + tick handler
    └── CombatHudOverlay.java                  # IGuiOverlay impl; anchor-aware positioning
```

## Known limitations

- **`/combat` and `/peace` collision.** These root literals are shared namespaces. If another mod or plugin registers the same name, behavior depends on registration order. Fall back to `/ct` and `/combattoggle <subcmd>` if needed.
- **Vanilla clients have no HUD or keybind.** Use `/ct` exclusively. By design.
- **No public API for other mods.** State lives on a Forge capability so a future minor version can add an API without re-architecting; nothing exposed today.

## License

[GPL-3.0](LICENSE)

## Credits

Developed by Runecraft. Original 1.1.0 port to 1.20.1 served as a structural reference.
