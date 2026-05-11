# Changelog

## 1.2.0

The 1.19.2 line's first server-installable release. Vanilla clients can now join modded servers running Combat Toggle and use `/ct` without installing anything client-side. Internal architecture rewritten around a Forge capability and a hybrid common/server/client package layout. **Breaking** for the 1.1.x persistent-timer schema (mode preserved; tags and cooldowns reset on first login).

### Server-installable, vanilla-client-tolerant

- `mods.toml` now declares `displayTest = "IGNORE_SERVER_VERSION"`. Vanilla clients no longer see the red ❌ in the multiplayer list when targeting a modded server.
- The network channel uses `NetworkRegistry.acceptMissingOr(PROTOCOL::equals)`. Vanilla clients pass the handshake; the channel quietly drops S2C packets to peers without it registered.
- Player commands are the new baseline UX for vanilla-client users:
  - `/ct` toggles
  - `/combat` and `/peace` set explicitly
  - `/combattoggle status` and `/combattoggle help` view + discover

### PvP enforcement hardening

- Primary cancel moved from `LivingHurtEvent` to `LivingAttackEvent` (HIGH priority). Stops the hurt pipeline before knockback, hurt sound, and the `PLAYER_HURT_ENTITY` advancement criterion fire — no more free knockback shots on Peace targets. `LivingHurtEvent` remains as defence-in-depth and clears residual `deltaMovement` + `hurtMarked`.
- Attacker resolution walks the standard ownership chain: direct hit → projectile owner → primed-TNT owner → tamed pet (`OwnableEntity`). Wolves, snow golems, ignited TNT are now correctly gated by Combat Toggle.

### Capability-backed state

- `CombatToggleData` is now a Forge capability attached to every `Player`. `CombatToggleData.get(player)` returns the same in-memory instance for the lifetime of that player — no per-call NBT alloc, no async-mutation races.
- Time-based fields migrated from wall-clock millis to game ticks (`level.getGameTime()`). Monotonic, persisted, restart-safe.
- Clone-on-respawn copies state via `PlayerEvent.Clone` with the standard `reviveCaps` / `invalidateCaps` dance.

### One-shot legacy NBT migration

- On first 1.2.0 login, the pre-1.2.0 `combat_toggle` compound on `player.getPersistentData()` is read. The `enabled` flag is preserved into the new capability; the wall-clock-millis timestamps (`lastToggleMs`, `combatTagUntilMs`, `lastPvpMs`) are dropped — they cannot be safely interpreted against the new game-tick clock. The legacy compound is removed after migration. Logged once per player as `Migrated legacy combat_toggle data for {name} (preserved mode={MODE}, dropped expired timers)`.

### Configurable HUD position

New client-config keys in `combattoggle-client.toml`:

| Key | Default | Notes |
|---|---|---|
| `hudEnabled` | `true` | Master switch |
| `hudShowInCombatMode` | `true` | Hide while in Combat |
| `hudShowInPeaceMode` | `true` | Hide while in Peace |
| `hudAnchor` | `TOP_CENTER` | 9-anchor + `CUSTOM` (absolute screen coords) |
| `hudOffsetX` | `0` | Inward pixel offset from anchor |
| `hudOffsetY` | `6` | Inward pixel offset from anchor |
| `hudScale` | `1.0` | 0.5 – 3.0 |
| `hudShowTimers` | `true` | Tag + cooldown countdown text |

The 1.1.0 server config key `showHud` is dropped — HUD visibility is purely client-side now. Operator configs keep the orphan key on first read with a Forge unused-key warning; harmless.

### Architecture

Source split into three packages:

- `common/` — config spec, capability state + registration, network packets. No client-class imports; loadable on dedicated server.
- `server/` — gameplay logic (commands, lifecycle events, PvP enforcement, scoreboard teams). Loaded on every logical server (dedicated and integrated). Zero `net.minecraft.client.*` references.
- `client/` — HUD overlay, keybind, client-side cache. Every subscriber gated with `@Mod.EventBusSubscriber(value = Dist.CLIENT)` so dedicated server never classloads them.

### Network

- Protocol version `"1"` (1.2.x line). 1.1.x modded clients are cleanly handshake-rejected; vanilla clients pass through `acceptMissing`.
- Wire format for `S2CSyncStatePacket`: `boolean | varInt seconds | varInt seconds`. ~5 bytes total.
- `C2SRequestTogglePacket` carries no payload — the server identifies the requester via `ctx.getSender()`.

### Files added (vs. 1.1.0 jar)

```
src/main/java/com/runecraft/combattoggle/
├── CombatToggle.java                            (entry)
├── common/CTConfig.java
├── common/HudAnchor.java                        (NEW — 9-anchor enum)
├── common/data/CombatToggleData.java
├── common/data/CombatToggleCapability.java      (NEW — capability registration)
├── common/network/PacketHandler.java
├── common/network/C2SRequestTogglePacket.java
├── common/network/S2CSyncStatePacket.java
├── server/CommandRegistry.java                  (renamed from events/CommandEvents)
├── server/ToggleService.java                    (NEW — central toggle business logic)
├── server/PlayerLifecycleEvents.java
├── server/CombatEnforcementEvents.java
├── server/TeamManager.java
├── server/TextHelper.java
├── client/ClientCombatState.java
├── client/ClientKeybinds.java
└── client/CombatHudOverlay.java
src/main/resources/
├── META-INF/mods.toml                           (UPDATED — displayTest, version)
├── pack.mcmeta
└── assets/combattoggle/
    ├── lang/en_us.json                          (UPDATED — added /ct + help_* keys)
    └── textures/gui/{combat,peace}.png          (reused from 1.1.0)
```

### Side-safety audit findings (resolved)

| Severity | Issue (1.1.0) | Resolution |
|---|---|---|
| P0 | `mods.toml` had no `displayTest` → vanilla clients saw `MATCH_VERSION` mismatch in server list | Set `displayTest="IGNORE_SERVER_VERSION"` |
| P0 | SimpleChannel handshake rejected vanilla clients (default behaviour without `acceptMissingOr`) | Use `NetworkRegistry.acceptMissingOr(PROTOCOL::equals)` on both sides |
| P1 | PvP enforcement only at `LivingHurtEvent` — knockback / hurt sound / advancement fired before cancel | Primary cancel moved to `LivingAttackEvent` (HIGH); LivingHurt is fallback |
| P1 | NBT timestamps were wall-clock ms with ambiguous source | Switched to game-tick deadlines; legacy migrator drops stale 1.1.x timers |
| P1 | HUD position hardcoded | Added 9-anchor + offset + scale config |
| P1 | No player-facing toggle command | Added `/ct`, `/combat`, `/peace`, `/combattoggle status` |
| P2 | Mixed `events/` package conflated server enforcement with command registration | Split into `common/` + `server/` + `client/` |
| P2 | `CombatToggleData` was a per-call NBT read/write pattern | Moved to a Forge capability |

### Known limitations

- `/combat` and `/peace` are common literal namespaces — collision risk with other mods/plugins. Fall back to `/ct` (low collision risk) and `/combattoggle <subcmd>` (uniquely namespaced).
- Vanilla clients have no HUD or keybind. Use `/ct`. By design.
- No public mod-integration API. The capability is internal; a future minor can expose it without re-architecting.
