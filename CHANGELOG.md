# Changelog

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
