# Changelog

## 1.2.1

Major release implementing every actionable item from the 1.1.5 code review (REVIEW.md).
**Breaking:** packet protocol bumps to v3 and the player-state schema migrates to a Forge capability with
ticks-based timers — clients on 1.1.x are kicked at handshake, and any in-flight tag/cooldown timers from
1.1.x saves are dropped on first load (mode flag is preserved).

### Bug Fixes
- **PvP enforcement now cancels at `LivingAttackEvent` instead of `LivingHurtEvent`.** Cancelling at the old event happened
  *after* knockback, hurt sound, and `PLAYER_HURT_ENTITY` had already fired — letting attackers chain free knockback shots
  on Peace targets. The new path stops the entire hurt pipeline before any side effects. `LivingHurtEvent` remains as a
  defence-in-depth fallback that also clears residual knockback velocity. (REVIEW 1.1, 3.4)
- **Indirect-damage PvP loopholes closed.** Attacker resolution now walks the full ownership chain: direct hits, projectiles,
  primed TNT (`PrimedTnt.getOwner()`), and any `OwnableEntity` (vanilla tamables and modded summons). Wolves, snow golems,
  and ignited TNT are now correctly gated by Combat Toggle. (REVIEW 1.2, 1.10)
- **Server-side timers migrated from wall-clock to game ticks.** `combat_tag_until_ms`, `last_toggle_ms`, and `last_pvp_ms`
  became `*_tick` fields driven by `level.getGameTime()` — monotonic, persistent across server restarts, and immune to
  NTP steps or admin clock changes. The HUD packet still carries millisecond remainders for client-side display.
  (REVIEW 1.3)
- **Admin `/set <player> peace` now respects `forceCombatWhileTagged`.** Previously the command silently flipped a tagged
  player to Peace even though the next PvP hit would rubber-band them back. Now refuses with a clear error unless
  `bypass=true` is supplied. (REVIEW 1.4)
- **`/combattoggle status` no longer reports a misleading cooldown for Peace-mode players.** Asks the cooldown question in
  the direction of the player's *next* possible toggle, not always TO_PEACE. (REVIEW 1.5)
- **Client state is reset on disconnect / reconnect.** `ClientCombatState` zeros itself on
  `ClientPlayerNetworkEvent.LoggingIn` and `LoggingOut` so the HUD never carries stale tag/cooldown state across servers.
  (REVIEW 1.6)
- **`/combattoggle tag <player> 0` rejected at the parser.** Brigadier now uses `integer(1, 3600)`; the no-args form
  surfaces a clearer error (`tag_disabled_in_config`) when the server has tagging disabled. (REVIEW 1.7)
- **Per-call NBT allocations eliminated for player state.** `CombatToggleData` is now a Forge capability attached to every
  `Player` entity. `CombatToggleData.get(player)` returns the same in-memory instance for the lifetime of that player —
  no per-call allocation, no read-modify-save footgun, no async-mutation race. `save()` is preserved as a no-op for
  source compatibility. (REVIEW 1.8, 1.13, 5.1)
- **`cooldownAppliesToPeaceOnly` boolean replaced with `cooldownScope` enum** (`PEACE_ONLY` / `COMBAT_ONLY` / `BOTH` / `NONE`).
  The boolean conflated two semantics and made the symmetric/none cases inexpressible. Boolean is dropped — operators on
  upgrade get the `PEACE_ONLY` default. (REVIEW 1.9, with related rename of `isCooldownActive(boolean)` →
  `isCooldownActiveForDirection(ToggleDirection)`, REVIEW 3.8.)
- **`/combattoggle reload` renamed to `/combattoggle resync`.** The actual operation is a team refresh + HUD resync —
  Forge auto-reloads the config on file save independently. The old `/reload` literal is preserved as an alias.
  (REVIEW 1.11)
- **Mode argument is now tab-completable.** `/combattoggle set <player> <combat|peace> [bypass]` uses literal subcommands;
  typos no longer reach the executor. (REVIEW 1.12)

### New Features
- **`/combattoggle help`** — permission-filtered usage listing for every subcommand. (REVIEW 3.9)
- **Damage-type denylist** — new `blockedDamageTypes` config (list of damage-type resource locations) lets admins
  forbid specific PvP vectors (e.g. `minecraft:magic`, `minecraft:trident`) without disabling PvP entirely. (REVIEW 3.1)
- **Public Forge event surface for mod integrations** — three new events under `com.runecraft.combattoggle.api.events`:
  - `CombatToggleStateChangeEvent` (cancellable, fires before persist; lets safe-zone mods refuse a toggle)
  - `CombatTagAppliedEvent` (fires when a tag is set or extended)
  - `CombatTagExpiredEvent` (fires when a tag elapses, including on next-login if it elapsed offline)
  - `CombatLoggedOutEvent` (fires on disconnect with remaining tag ticks; canonical combat-log signal for penalty mods)
  (REVIEW 3.2, 3.12, 3.13)
- **Configurable HUD position.** New client-config keys `hudAnchor` (LEFT/CENTER/RIGHT), `hudYOffset` (pixels), and
  `showHudTimers` (boolean) so HUD-customisation users can move or hide the indicator. (REVIEW 3.5)
- **Tag-expired chat ping fires after offline-elapsed tags too.** A new `tag_expiry_notified` persisted flag ensures the
  notification is sent exactly once whether the tag expired live or while the player was offline. (REVIEW 3.11)
- **Force-combat flips notify the target.** When `forceCombatWhileTagged` flips a Peace player to Combat (PvP hit or login
  while tagged), the player gets a chat hint explaining why and how long the tag has left. (REVIEW 3.10)

### Performance
- **Tick handler stops allocating once per tagged player per tick.** Tag deadlines are cached in a `Map<UUID, Long>`
  alongside the tracker — the per-tick walk is one iterator + one `>` comparison, no NBT reads, no `CombatToggleData`
  allocations. (REVIEW 2.1)
- **HUD timer strings cached per-second.** Replaces the per-frame string concat + font.width measurement with a
  one-render-per-second rebuild. (REVIEW 2.2)
- **Sync packet shrunk from 17 bytes to ~5.** Two longs replaced with two varint seconds (HUD only displays mm:ss).
  Packet protocol bumps to "3" — clients on 1.1.x get a clean handshake reject. (REVIEW 2.3, 4.12)
- **`isPvpAllowed` centralised in `CombatToggleData`.** Single source of truth used by both the API and enforcement; eliminates
  duplicated `requireBoth` config reads. (REVIEW 2.4, 2.5)
- **Team-name pair cached at config load/reload** instead of resolved on every login/respawn/toggle/admin-set. (REVIEW 2.6)
- **Hot-path PvP debug logging guarded by `LOGGER.isDebugEnabled()`** to avoid eager arg evaluation on the per-hit path.
  (REVIEW 2.7)

### Internals
- New `data/CombatToggleCapability.java` registers the capability and attaches a provider to every `Player`. The clone hook
  replaces the old `PlayerEvent.Clone`-based persistent-data copy.
- New `data/ToggleDirection.java` and `data/CooldownScope.java` enums replace boolean direction flags.
- One-shot legacy NBT migration (`PlayerLifecycleEvents.migrateLegacyData`) ports `enabled` from any pre-1.2.0
  `combat_toggle` persistent-data compound and discards the wall-clock timestamps (which would compare as in-the-future
  against the new game-tick clock). The legacy compound is removed after migration.
- API method `CombatToggleAPI.isCooldownActive(ServerPlayer, boolean)` deprecated for removal in favour of the
  `ToggleDirection` overload.
- The lang file gained 12 new keys (help_*, set_blocked_tagged, tag_disabled_in_config, admin_force_flipped,
  pvp_blocked_damage_type, state_change_cancelled, resync) and the `reload` key was renamed.

### Skipped / Out-of-Scope
- **JEI/EMI integration page (REVIEW 3.14)** — would require adding compileOnly external Maven deps and a custom anchor
  item to host the info page. The `/combattoggle help` command added in this release covers the same discoverability gap
  with no extra dependencies.

## 1.1.5

### Bug Fixes
- **Ghost-tagged players after relog.** If a player logged out while combat-tagged and logged back in within the tag window, the tick handler's in-memory tracking set was not repopulated — the NBT tag persisted (toggle restrictions still applied) but the `tag_expired` chat notification never fired. `PlayerLifecycleEvents.syncAndEnforce` now re-registers tagged players with the tick handler on login and respawn.
- **`/combattoggle reload` left scoreboard team formatting stale.** Forge auto-reloads `combattoggle-server.toml` on file change, but edits to `combatEmoji`, `peaceEmoji`, `useNameplateColors`, or the team names only took effect after a server restart. The command now calls `TeamManager.ensureTeamsExist` before resyncing players, so color/prefix edits apply immediately.
- **Rate-limited toggle requests were silently dropped.** Mashing the toggle keybind caused the second-and-subsequent packets to drop server-side with no chat feedback and no state resync. Rate-limit drops now return the current state (keeping the HUD in sync) and emit a throttled `combattoggle.msg.toggle_rate_limited` hint.
- **Projectile PvP bypass — hardened attacker resolution.** Attacker lookup for projectile damage now also falls through `event.getSource().getDirectEntity()` to cover cases where `getEntity()` is null (owner logged out, despawned chunk, etc.).
- **Zero-second `/combattoggle tag` is now rejected** with a hint to use `/combattoggle untag` instead of silently creating an already-expired tag.
- **Team-name collision detection.** If `combatTeamName` equals `peaceTeamName`, scoreboard team logic is disabled with a warning instead of producing identical-colored nameplates.
- **`PlayerEvent.Clone` persistent-data copy narrowed** to only the mod's own `combat_toggle` compound, so other mods' persistent data is untouched across death/respawn.

### New Features
- **`/combattoggle status` command** — available to all players (no OP required). Shows current mode, active combat tag remaining, and cooldown remaining.
- **Tag expiration notifications.** New `CombatTagTickHandler` emits `combattoggle.msg.tag_expired` when a player's tag elapses, so players know when combat logging penalties (if configured by external tooling) no longer apply.
- **Toggle rate limiting.** Server rejects toggle requests arriving within 500ms of the previous one, preventing state churn from held keys or misbehaving clients.
- **Throttled PvP-blocked feedback.** Attackers who hit a peace-mode target now see a chat message explaining why the hit was cancelled (throttled to once per 5 seconds per attacker to avoid spam).
- **Public API (`com.runecraft.combattoggle.api.CombatToggleAPI`).** Stable server-side accessors for `isInCombatMode`, `isInPeaceMode`, `isCombatTagged`, `getCombatTagRemainingMs`, `isPvpAllowed`, `isCooldownActive`, `getCooldownRemainingMs`. Other mods can integrate without touching internals.
- **Comprehensive structured logging.** DEBUG/INFO events for PvP decisions, toggle requests, command invocations, team assignments, and lifecycle events — pipe through to SLF4J like any other mod.
- **Accurate cooldown denial messages.** `/combattoggle status` and the toggle denial chat text now report the actual cooldown source (PvP activity vs. recent toggle) instead of always saying "PvP activity". Backed by the new `CombatToggleData.CooldownState` record so both checks share one calculation path.

### Improvements
- **Peace and Combat HUD textures refined.** Repainted the Peace (shield + leaves) and Combat (crossed swords) 51×19 GUI icons with cleaner bevels, higher-contrast fills, and crisper pixel-art detail. File size grew from ~500B to ~4KB per icon; the visual change is visible at all GUI scales.
- **Default toggle keybind switched from Caps Lock to V.** Caps Lock was a poor default — it remains toggled on after the game releases the key, interfering with other client software. V is free on the vanilla key map. Existing players with a custom binding are unaffected.
- **Full translation key migration.** Every player-facing message now routes through `combattoggle.*` translation keys in `en_us.json`. Ready for community translations without code edits.
- **Config split into SERVER and CLIENT specs.** `SPEC` → `SERVER_SPEC` + `CLIENT_SPEC`, registered as their own files (`combattoggle-server.toml` / `combattoggle-client.toml`). Server-side options (cooldowns, teams, enforcement) no longer leak into single-player client configs, and `showHud` is properly per-client.
- **Conditional S2C state sync.** PvP events only emit a sync packet when the attacker's or victim's state actually changed, cutting needless client chatter in PvE-heavy scenarios.
- **`updatePlayerTeam` now only disturbs Combat Toggle teams** — if another mod has placed the player in its own scoreboard team, that assignment is preserved.
- **Encapsulated `CombatToggleData` fields.** Public mutable fields (`.enabled`, `.lastToggleMs`, `.combatTagUntilMs`, `.lastPvpMs`) replaced with getters/setters. Safer for future internal changes.
- **README rewritten** to match actual feature set, v1.1.x command surface, and the V keybind default.

### Internals
- New `CombatTagTickHandler` (server tick, phase END) drives tag expiration notifications.
- New `CombatToggleData.CooldownState` record collapses three duplicate cooldown-math sites into one `resolveCooldown(now)` path.
- Packet network protocol remains at v2 (no wire-format change since 1.1.4).

## 1.1.4

### Bug Fixes
- **Projectile PvP bypass (B1).** Arrows, tridents, splash potions, and other projectile attacks now correctly respect PvP toggle rules. Previously, if the causing entity reference was lost (e.g., shooter logged out mid-flight), projectile damage could bypass enforcement entirely. The handler now falls back to resolving the attacker through the projectile's direct entity and its owner. A self-damage guard also prevents a player's own projectiles from triggering PvP logic.
- **Victims now notified when combat-tagged (B2).** Players receive a chat message when first tagged by PvP combat, showing the tag duration. Subsequent hits while already tagged do not spam notifications.
- **Reload command now functional (B4).** `/combattoggle reload` refreshes scoreboard teams and syncs HUD state for all online players, instead of being a no-op.
- **Combat tag timer used absolute server timestamp.** The HUD tag timer was comparing the server's absolute clock against the client's, causing incorrect display under clock skew. Both tag and cooldown timers now use relative durations converted to client-local timestamps.

### New Features
- **HUD cooldown and tag timers (E1).** The HUD overlay now displays a countdown timer for active combat tags (red) and cooldowns (orange) below the mode icon. Both timers can display simultaneously, stacked vertically.
- **Configurable scoreboard team names (E4).** New `combatTeamName` and `peaceTeamName` config options allow customizing the scoreboard team names (default: `ct_combat`/`ct_peace`).
- **Scoreboard teams can be disabled (B3).** New `useScoreboardTeams` config option (default: `true`). When disabled, players are removed from Combat Toggle teams, avoiding conflicts with other mods that use scoreboard teams.

### Improvements
- **Team cleanup on disable.** When `useScoreboardTeams` is set to `false`, existing team assignments are cleaned up automatically instead of being left orphaned.
- **Network protocol bumped to v2.** The sync packet format changed to support timer data. Clients with mismatched mod versions will get a clean disconnect instead of a deserialization crash.

## 1.1.0

### Bug Fixes
- **Cooldown reset command now clears both cooldown types.** Previously, `/combattoggle resetcooldown` only reset the toggle-based cooldown (`lastToggleMs`) but left the PvP-triggered cooldown (`lastPvpMs`) active. Both are now reset to zero.
- **Admin `set` command no longer triggers unintended cooldowns.** The command was unconditionally setting `lastToggleMs`, starting a toggle-based cooldown even when that cooldown type was disabled in config. It now respects the `cooldownTriggersOnToggle` setting.
- **Fixed HUD texture rendering.** The texture dimension constants (450x101) did not match the actual texture files, causing incorrect UV sampling in the `blit()` call. Constants now match the real texture dimensions.
- **Removed unused `allowClientButtonClick` config option.** This setting was defined but never referenced anywhere in the codebase.

### Improvements
- **New GUI textures.** Replaced the plain colored rectangles with Minecraft-style beveled GUI icons featuring stone-gray borders, tinted inner fills, and pixel-art icons (shield for Peace, crossed swords for Combat).
- **Compact HUD indicator.** Reduced texture size from 120x27 to 51x19 for a less intrusive on-screen presence.
- **Cleaner toggle messages.** Removed the noisy cooldown suffix from mode switch messages. Toggle feedback now simply reads "Mode set to: COMBAT" or "Mode set to: PEACE".

### Repository
- Fixed license mismatch: `mods.toml` now correctly declares GPL-3.0 (was MIT)
- Added GitHub Actions CI workflow for automated build verification
- Fixed `gradlew` line endings (CRLF to LF)

## 1.0.0

- Initial release
- Peace/Combat mode toggle with Caps Lock keybind
- HUD overlay showing current mode
- Combat tagging system with configurable duration
- PvP-triggered and toggle-triggered cooldown system
- Nameplate text prefixes and color coding via scoreboard teams
- Persistent player state via NBT
- Full admin command suite (`get`, `set`, `resetcooldown`, `tag`, `untag`, `reload`)
- Server-side configuration via `combattoggle.toml`
