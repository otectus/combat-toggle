# Combat Toggle

**A server-installable PvP opt-in system with combat tagging, smart cooldowns, and a full admin toolkit — for Minecraft 1.19.2 Forge.**

Combat Toggle lets every player on your server choose whether they can be attacked. It's a ship-it-on-a-modpack solution for the "some players want PvP, some don't" problem — no world-border workarounds, no region plugins, no third-party dependencies. **Install it on your dedicated server and vanilla clients can join and use `/ct` without touching their game.**

***

## What it does

*   **Peace mode** — PvP is off. Other players' arrows, swords, potions, and projectiles pass harmlessly.
*   **Combat mode** — PvP is on. You can be hit, and you can hit back.
*   Each player decides independently. The default is Peace — new players must opt in.
*   **Server-installable.** Vanilla clients can join and use `/ct` to flip modes with no client install. Modded clients additionally get a rebindable `V` keybind and an on-screen HUD indicator.

***

## Features

### Core

*   **Universal commands.** `/ct`, `/combat`, `/peace`, and `/combattoggle status` work from any client — modded or vanilla.
*   **One-key toggle.** Default keybind: `V` (modded clients only). Fully rebindable per-client.
*   **HUD overlay.** 51×19 pixel-art icon (shield for Peace, crossed swords for Combat). Configurable position (9-anchor + custom XY), scale, and per-mode visibility. Hides automatically in inventories and when the HUD is toggled off with `F1`.
*   **Combat tagging.** Any PvP engagement tags both attacker and victim for a configurable duration (default 30s). While tagged, a player cannot switch to Peace mode — no combat-logging via the toggle.
*   **Smart cooldowns.** By default, the cooldown starts only when PvP actually happens, not when players toggle. Players can freely switch modes while farming, building, or exploring.
*   **Persistent state.** Mode, tag timer, and cooldown survive logouts, respawns, dimension changes, and server restarts. Stored on a Forge capability serialized to player data.
*   **Dual-event PvP enforcement.** Primary cancel at `LivingAttackEvent` (HIGH priority) — knockback, hurt sound, and the `PLAYER_HURT_ENTITY` advancement criterion never fire on Peace targets. `LivingHurtEvent` runs as defence-in-depth. Attacker resolution walks projectile owners, primed-TNT owners, and tamed pets, so wolves, snow golems, and ignited TNT are correctly attributed.

### Social

*   **Nameplate coloring.** Players in Combat mode get red nameplates; Peace players get blue. Disable if you already use scoreboard teams for something else.
*   **Emoji/text prefixes.** Configurable glyphs (defaults: crossed swords and a coffee mug) appear before the player name so the state is unmistakable from any angle.
*   **Throttled feedback.** Attackers who hit a peace-mode target get a one-line chat message explaining why nothing happened (throttled so it never spams).

### Player commands

No permissions required.

| Command                                       |Purpose                                           |
| --------------------------------------------- |------------------------------------------------- |
| <code>/ct</code>                              |Toggle Combat ↔ Peace.                            |
| <code>/combat</code>                          |Set yourself to Combat mode.                      |
| <code>/peace</code>                           |Set yourself to Peace mode.                       |
| <code>/combattoggle status</code>             |Show your own mode, tag, and cooldown.            |
| <code>/combattoggle help</code>               |List every command you can run (admin entries hidden for non-OP). |

### Admin commands

Require OP level 2.

| Command                                                    |Purpose                                                 |
| ---------------------------------------------------------- |------------------------------------------------------- |
| <code>/combattoggle get &lt;player&gt;</code>              |Inspect another player's state.                         |
| <code>/combattoggle set &lt;player&gt; &lt;peace\|combat&gt; [bypass]</code> |Force a mode. <code>bypass=true</code> overrides cooldown + tag guard. |
| <code>/combattoggle tag &lt;player&gt; [seconds]</code>    |Apply a combat tag manually (1–3600s).                  |
| <code>/combattoggle untag &lt;player&gt;</code>            |Clear a combat tag.                                     |
| <code>/combattoggle resetcooldown &lt;player&gt;</code>    |Clear both toggle and PvP cooldowns.                    |
| <code>/combattoggle resync</code>                          |Refresh scoreboard teams and resync HUD state for everyone online (<code>/combattoggle reload</code> is an alias). |

### For server owners and modpack authors

*   **Capability-backed state.** Player data lives on a Forge capability (`CombatToggleData`); same in-memory instance for the player's whole session, no per-call NBT churn. There's no public API today, but a future minor can expose one without re-architecting.
*   **Structured SLF4J logging** at DEBUG/INFO for every PvP decision, toggle request, and command invocation. Plays nicely with ops pipelines.
*   **Translation-driven messages.** Every player-facing string is a translation key (`combattoggle.msg.*`). Ship translations without code patches.
*   **Vanilla-tolerant network handshake.** The channel uses `NetworkRegistry.acceptMissingOr(PROTOCOL::equals)`; vanilla clients pass through, sync packets are dropped silently to peers without the channel.

***

## Installation

1.  Install [Minecraft Forge 43.3.0+](https://files.minecraftforge.net/) for Minecraft 1.19.2.
2.  Drop `combattoggle-1.2.0.jar` into your server's (or client's) `mods/` folder.
3.  Launch. On first run, `combattoggle-server.toml` is generated in your world's `serverconfig/` directory and `combattoggle-client.toml` in the client config folder.

**Server-installable?** Yes. Install on the dedicated server only and vanilla clients can join and use `/ct`, `/combat`, `/peace`, and `/combattoggle status` with no client-side install. Players who additionally install the mod client-side get the `V` keybind and the HUD indicator on top — eye candy over the same server-authoritative logic.

***

## Configuration

### `combattoggle-server.toml`

| Key                                   |Default              |What it does                                                         |
| ------------------------------------- |-------------------- |-------------------------------------------------------------------- |
| <code>cooldownSeconds</code>          |<code>600</code>     |Cooldown duration (10 min). Set to <code>0</code> to disable cooldown entirely. |
| <code>combatTagSeconds</code>         |<code>30</code>      |How long a combat tag lasts after a PvP event.                       |
| <code>requireBothCombatEnabled</code> |<code>true</code>    |If <code>true</code>, PvP only works when both attacker <strong>and</strong> target are in Combat. |
| <code>forceCombatWhileTagged</code>   |<code>true</code>    |Force tagged players into Combat mode.                               |
| <code>allowToggleWhileTagged</code>   |<code>false</code>   |If <code>false</code>, Combat-tagged players cannot switch to Peace. |
| <code>cooldownTriggersOnPvp</code>    |<code>true</code>    |Cooldown starts when a player deals or takes PvP damage.             |
| <code>cooldownTriggersOnToggle</code> |<code>false</code>   |Cooldown starts when a player toggles.                               |
| <code>cooldownAppliesToPeaceOnly</code> |<code>true</code>    |Cooldown only blocks Combat→Peace; Peace→Combat is always instant.   |
| <code>allowAdminBypassCooldown</code> |<code>true</code>    |<code>bypass=true</code> on <code>/combattoggle set</code> actually works. |
| <code>useScoreboardTeams</code>       |<code>true</code>    |Disable to keep Combat Toggle out of the scoreboard entirely.        |
| <code>useEmojiPrefixes</code> / <code>useNameplateColors</code> |<code>true</code> / <code>true</code> |Independent toggles for the two visual effects.                      |
| <code>combatEmoji</code> / <code>peaceEmoji</code> |<code>⚔</code> / <code>☕</code> |Any BMP Unicode.                                                     |
| <code>combatTeamName</code> / <code>peaceTeamName</code> |<code>ct_combat</code> / <code>ct_peace</code> |Rename to avoid clashes.                                             |

### `combattoggle-client.toml`

| Key                                  |Default              |What it does                                                              |
| ------------------------------------ |-------------------- |------------------------------------------------------------------------- |
| <code>hudEnabled</code>              |<code>true</code>    |Master switch for the HUD icon.                                           |
| <code>hudShowInCombatMode</code>     |<code>true</code>    |Show the icon while in Combat mode.                                       |
| <code>hudShowInPeaceMode</code>      |<code>true</code>    |Show the icon while in Peace mode.                                        |
| <code>hudAnchor</code>               |<code>TOP_CENTER</code> |One of <code>TOP_LEFT</code>, <code>TOP_CENTER</code>, <code>TOP_RIGHT</code>, <code>CENTER_LEFT</code>, <code>CENTER</code>, <code>CENTER_RIGHT</code>, <code>BOTTOM_LEFT</code>, <code>BOTTOM_CENTER</code>, <code>BOTTOM_RIGHT</code>, <code>CUSTOM</code>. |
| <code>hudOffsetX</code>              |<code>0</code>       |Inward pixel offset from the anchor. Absolute screen X if <code>hudAnchor=CUSTOM</code>. |
| <code>hudOffsetY</code>              |<code>6</code>       |Inward pixel offset from the anchor. Absolute screen Y if <code>hudAnchor=CUSTOM</code>. |
| <code>hudScale</code>                |<code>1.0</code>     |Range 0.5–3.0.                                                            |
| <code>hudShowTimers</code>           |<code>true</code>    |Show tag/cooldown countdown text below the icon.                          |

***

## Upgrading from 1.1.x

*   **Mode is preserved.** Whatever mode (Combat or Peace) each player was last in carries through on first 1.2.0 login.
*   **In-flight tags and cooldowns reset on first 1.2.0 login.** The 1.1.x persistent timers were wall-clock milliseconds with no clock-source guarantee; converting them to the new game-tick clock isn't safe, so they're dropped. New tags/cooldowns from 1.2.0 onward use a monotonic, restart-safe game-tick deadline.
*   **The `showHud` server config key is gone.** HUD visibility is purely client-side now via `hudEnabled` in `combattoggle-client.toml`. Existing operator configs keep the orphan key on first read; Forge logs an unused-key warning. Harmless.
*   **Network protocol is `"1"` for the 1.2.x line.** 1.1.x modded clients cannot connect to a 1.2.0 server (handshake rejects); they need to upgrade. Vanilla clients are unaffected.

***

## Compatibility

*   **Minecraft:** 1.19.2
*   **Forge:** 43.3.0 or newer
*   **Java:** 17
*   **Dependencies:** None. No Kotlin, no Architectury, no Cloth Config.
*   **Other PvP mods:** Should coexist with anti-cheat, region protection, and KubeJS. `useScoreboardTeams=false` resolves conflicts with mods that own the scoreboard.
*   **Multiplayer-only?** No. Single-player works; the toggle still applies to damage interactions with hostile iron golems, etc. (as you'd expect for "PvP enabled/disabled").

***

## Known limitations

*   **Mixin-based damage sources may bypass enforcement.** Combat Toggle cancels at both `LivingAttackEvent` (primary) and `LivingHurtEvent` (fallback). A handful of mods route damage through custom mixins that bypass *both* — those will hit Peace targets. Open an issue with a reproduction if you hit this.
*   **No dimension allowlist.** PvP rules apply uniformly across every dimension.
*   **`/combat` and `/peace` are common literal namespaces.** If another mod or plugin registers the same root, behavior depends on registration order — fall back to `/ct` and `/combattoggle <subcmd>`.
*   **No public mod-integration API today.** Player state lives on an internal Forge capability; a future minor can expose it without re-architecting.
*   **Vanilla clients have no HUD or keybind.** Use `/ct`. By design.

***

## License & Source

*   **License:** GPL-3.0
*   **Source & issues:** [https://github.com/otectus/combat-toggle](https://github.com/otectus/combat-toggle)
*   **Modpack home:** Built for the Runecraft 1.19.2 Forge modpack. Drop it into anything else — it's standalone.

Pull requests and bug reports welcome. Please include server logs, your `combattoggle-server.toml`, and a minimal repro when filing an issue.
