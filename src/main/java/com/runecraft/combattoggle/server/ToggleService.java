package com.runecraft.combattoggle.server;

import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.common.CTConfig;
import com.runecraft.combattoggle.common.data.CombatToggleData;
import com.runecraft.combattoggle.common.network.PacketHandler;
import com.runecraft.combattoggle.common.network.S2CSyncStatePacket;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central business logic for toggling Combat / Peace mode. Called by:
 * <ul>
 *   <li>{@code /ct}, {@code /combat}, {@code /peace}, and {@code /combattoggle toggle} — vanilla-friendly path</li>
 *   <li>{@code C2SRequestTogglePacket.handle} — modded-keybind path</li>
 *   <li>{@code /combattoggle set} — admin path with optional bypass</li>
 * </ul>
 *
 * <p>Every successful state mutation finishes with a best-effort
 * {@link PacketHandler#sendToPlayer(ServerPlayer, Object)}: vanilla clients silently ignore it,
 * modded clients update their HUD.
 */
public final class ToggleService {

    /** Rate-limit for the keybind path only. The command path is naturally rate-limited by typing speed. */
    private static final long MIN_KEYBIND_INTERVAL_MS = 500L;
    private static final long RATE_LIMIT_MSG_THROTTLE_MS = 5000L;

    private static final Map<UUID, Long> lastKeybindRequestMs = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastRateLimitMsgMs = new ConcurrentHashMap<>();

    private ToggleService() {}

    public static void clearPlayerEntries(UUID playerId) {
        lastKeybindRequestMs.remove(playerId);
        lastRateLimitMsgMs.remove(playerId);
    }

    /** Keybind-driven toggle (rate-limited). */
    public static void requestToggleFromKeybind(ServerPlayer p) {
        long nowMs = Util.getMillis();
        UUID id = p.getUUID();
        Long last = lastKeybindRequestMs.get(id);
        if (last != null && (nowMs - last) < MIN_KEYBIND_INTERVAL_MS) {
            CombatToggle.LOGGER.debug("Toggle rate-limited for {}", p.getScoreboardName());
            // Resync so the HUD doesn't drift from server truth, and chat-warn (throttled).
            sendSync(p);
            Long lastMsg = lastRateLimitMsgMs.get(id);
            if (lastMsg == null || (nowMs - lastMsg) >= RATE_LIMIT_MSG_THROTTLE_MS) {
                p.sendSystemMessage(Component.translatable("combattoggle.msg.toggle_rate_limited"));
                lastRateLimitMsgMs.put(id, nowMs);
            }
            return;
        }
        lastKeybindRequestMs.put(id, nowMs);
        toggle(p);
    }

    /** Command-driven toggle (no rate limit; commands self-throttle). */
    public static void toggle(ServerPlayer p) {
        long nowTick = p.getLevel().getGameTime();
        CombatToggleData d = CombatToggleData.get(p);
        boolean wantCombat = !d.isEnabled();

        if (denyForTagOrCooldown(p, d, nowTick, wantCombat)) {
            sendSync(p);
            return;
        }

        d.setEnabled(wantCombat);
        if (CTConfig.cooldownTriggersOnToggle.get()) {
            d.setLastToggleTick(nowTick);
        }
        TeamManager.updatePlayerTeam(p, d.isEnabled());

        Component mode = TextHelper.modeName(d.isEnabled());
        CombatToggle.LOGGER.info("Player {} toggled to {}", p.getScoreboardName(), d.isEnabled() ? "COMBAT" : "PEACE");
        p.sendSystemMessage(Component.translatable("combattoggle.msg.mode_set", mode));
        sendSync(p);
    }

    /** Explicit set-to-mode (used by {@code /combat} and {@code /peace}). No-op if already in that mode. */
    public static void setMode(ServerPlayer p, boolean wantCombat) {
        CombatToggleData d = CombatToggleData.get(p);
        if (d.isEnabled() == wantCombat) {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.already_in_mode", TextHelper.modeName(wantCombat)));
            sendSync(p);
            return;
        }
        toggle(p);
    }

    /** Admin path: force-set with optional bypass for cooldown + tag guards. */
    public static boolean adminSet(ServerPlayer target, boolean wantCombat, boolean bypass) {
        long nowTick = target.getLevel().getGameTime();
        CombatToggleData d = CombatToggleData.get(target);

        if (!wantCombat && d.isTagged(nowTick) && CTConfig.forceCombatWhileTagged.get() && !bypass) {
            return false; // caller surfaces the failure message with TAG context
        }
        if (!bypass || !CTConfig.allowAdminBypassCooldown.get()) {
            if (d.isCooldownActiveForDirection(nowTick, wantCombat)) {
                return false; // caller surfaces the failure message with COOLDOWN context
            }
        }

        d.setEnabled(wantCombat);
        if (CTConfig.cooldownTriggersOnToggle.get()) {
            d.setLastToggleTick(nowTick);
        }
        TeamManager.updatePlayerTeam(target, d.isEnabled());
        target.sendSystemMessage(Component.translatable("combattoggle.msg.admin_set_mode", TextHelper.modeName(wantCombat)));
        sendSync(target);
        return true;
    }

    public static void applyTag(ServerPlayer target, int seconds) {
        long nowTick = target.getLevel().getGameTime();
        CombatToggleData d = CombatToggleData.get(target);
        long until = nowTick + (seconds * CombatToggleData.TICKS_PER_SECOND);
        if (until > d.getCombatTagUntilTick()) d.setCombatTagUntilTick(until);
        if (CTConfig.forceCombatWhileTagged.get()) {
            d.setEnabled(true);
            TeamManager.updatePlayerTeam(target, true);
        }
        target.sendSystemMessage(Component.translatable("combattoggle.msg.you_tagged", seconds));
        sendSync(target);
    }

    public static void clearTag(ServerPlayer target) {
        CombatToggleData d = CombatToggleData.get(target);
        d.setCombatTagUntilTick(0L);
        d.setTagExpiryNotified(true);
        target.sendSystemMessage(Component.translatable("combattoggle.msg.tag_cleared"));
        sendSync(target);
    }

    public static void resetCooldown(ServerPlayer target) {
        CombatToggleData d = CombatToggleData.get(target);
        d.setLastToggleTick(0L);
        d.setLastPvpTick(0L);
        sendSync(target);
    }

    public static void sendSync(ServerPlayer p) {
        long nowTick = p.getLevel().getGameTime();
        CombatToggleData d = CombatToggleData.get(p);
        PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(
                d.isEnabled(),
                d.getCombatTagRemainingMs(nowTick),
                d.getRemainingCooldownMs(nowTick)));
    }

    private static boolean denyForTagOrCooldown(ServerPlayer p, CombatToggleData d, long nowTick, boolean wantCombat) {
        if (d.isTagged(nowTick) && !CTConfig.allowToggleWhileTagged.get() && !wantCombat) {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.cannot_toggle_tagged",
                    TextHelper.formatRemaining(d.getCombatTagRemainingMs(nowTick))));
            return true;
        }
        if (d.isCooldownActiveForDirection(nowTick, wantCombat)) {
            CombatToggleData.CooldownSource src = d.getDominantCooldownSource(nowTick);
            Component reason = src == CombatToggleData.CooldownSource.PVP
                    ? Component.translatable("combattoggle.msg.cooldown_reason_pvp")
                    : Component.translatable("combattoggle.msg.cooldown_reason_toggle");
            p.sendSystemMessage(Component.translatable("combattoggle.msg.cooldown_active", reason,
                    TextHelper.formatRemaining(d.getRemainingCooldownMs(nowTick))));
            return true;
        }
        return false;
    }
}
