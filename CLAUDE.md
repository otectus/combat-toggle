# Combat Toggle — Forge 1.20.1 Mod

## Quick Reference
- **Mod ID**: `combattoggle`
- **Package**: `com.runecraft.combattoggle`
- **Version**: 1.2.1
- **MC**: 1.20.1 | **Forge**: 47.3.0 | **Java**: 17
- **Mappings**: Official
- **Network protocol**: v3 (varint sync packet — wire-format break vs 1.1.x)

## Build
- `./gradlew build` — full build
- `./gradlew compileJava` — compile-only

## Key Dependencies
- Forge only (no external mod dependencies)

## Architecture Notes
- PvP mode toggle system with combat tagging and cooldowns
- No mixins
- Player state lives on a Forge **capability** (`CombatToggleCapability.CAPABILITY` → `CombatToggleData`); attached
  to every `Player` via `AttachCapabilitiesEvent.Entity`. `CombatToggleData.get(player)` returns the same instance
  for the lifetime of that player — no per-call allocation. `save(player)` is a no-op kept for source compatibility.
- All persistent timers are **game ticks** (`level.getGameTime()`), not wall-clock millis. Survives server restarts;
  immune to NTP steps. Client HUD math still uses `Util.getMillis()` for monotonic in-process display.
- Public mod-integration surface: `com.runecraft.combattoggle.api.CombatToggleAPI` + four Forge events under
  `com.runecraft.combattoggle.api.events.*` (`CombatToggleStateChangeEvent`, `CombatTagAppliedEvent`,
  `CombatTagExpiredEvent`, `CombatLoggedOutEvent`).
