package com.runecraft.combattoggle.api;

import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.data.CombatToggleData;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public API for other mods to query Combat Toggle state.
 * All methods require a {@link ServerPlayer} and must be called server-side.
 * This class is the stable public contract; internal classes may change between versions.
 */
public final class CombatToggleAPI {

    private CombatToggleAPI() {}

    /**
     * Returns true if the player is in Combat mode (PvP enabled).
     */
    public static boolean isInCombatMode(ServerPlayer player) {
        return CombatToggleData.get(player).isEnabled();
    }

    /**
     * Returns true if the player is in Peace mode (PvP disabled).
     */
    public static boolean isInPeaceMode(ServerPlayer player) {
        return !CombatToggleData.get(player).isEnabled();
    }

    /**
     * Returns true if the player is currently combat-tagged.
     */
    public static boolean isCombatTagged(ServerPlayer player) {
        return CombatToggleData.get(player).isTagged(System.currentTimeMillis());
    }

    /**
     * Returns the remaining combat tag duration in milliseconds, or 0 if not tagged.
     */
    public static long getCombatTagRemainingMs(ServerPlayer player) {
        CombatToggleData d = CombatToggleData.get(player);
        long now = System.currentTimeMillis();
        return d.isTagged(now) ? d.getCombatTagUntilMs() - now : 0L;
    }

    /**
     * Returns true if PvP is currently allowed between attacker and victim,
     * based on the current configuration and both players' modes.
     */
    public static boolean isPvpAllowed(ServerPlayer attacker, ServerPlayer victim) {
        CombatToggleData a = CombatToggleData.get(attacker);
        CombatToggleData v = CombatToggleData.get(victim);
        boolean requireBoth = CTConfig.requireBothCombatEnabled.get();
        return requireBoth ? (a.isEnabled() && v.isEnabled()) : a.isEnabled();
    }

    /**
     * Returns true if the player has an active cooldown preventing mode change.
     * @param wantPeace true if checking whether toggling to Peace is blocked
     */
    public static boolean isCooldownActive(ServerPlayer player, boolean wantPeace) {
        return CombatToggleData.get(player).isCooldownActive(System.currentTimeMillis(), wantPeace);
    }

    /**
     * Returns the remaining cooldown in milliseconds, or 0 if no cooldown.
     */
    public static long getCooldownRemainingMs(ServerPlayer player) {
        return CombatToggleData.get(player).getRemainingCooldown(System.currentTimeMillis());
    }
}
