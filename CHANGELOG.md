# Changelog

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
