package com.runecraft.combattoggle.api;

import com.runecraft.combattoggle.data.CombatToggleData;
import com.runecraft.combattoggle.data.ToggleDirection;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public API for other mods to query Combat Toggle state.
 * All methods require a {@link ServerPlayer} and must be called server-side.
 * This class is the stable public contract; internal classes may change between versions.
 */
public final class CombatToggleAPI {

    private CombatToggleAPI() {}

    private static long nowTick(ServerPlayer player) {
        return player.serverLevel().getGameTime();
    }

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
        return CombatToggleData.get(player).isTagged(nowTick(player));
    }

    /**
     * Returns the remaining combat tag duration in milliseconds, or 0 if not tagged.
     */
    public static long getCombatTagRemainingMs(ServerPlayer player) {
        return CombatToggleData.get(player).getCombatTagRemainingMs(nowTick(player));
    }

    /**
     * Returns true if PvP is currently allowed between attacker and victim,
     * based on the current configuration and both players' modes.
     */
    public static boolean isPvpAllowed(ServerPlayer attacker, ServerPlayer victim) {
        return CombatToggleData.isPvpAllowed(attacker, victim);
    }

    /**
     * Returns true if the player has an active cooldown that would block a transition in the given direction.
     * Use {@link ToggleDirection#nextFor(boolean)} when you want to check the player's <em>next</em> possible toggle.
     */
    public static boolean isCooldownActive(ServerPlayer player, ToggleDirection direction) {
        return CombatToggleData.get(player).isCooldownActiveForDirection(nowTick(player), direction);
    }

    /**
     * @deprecated since 1.2.1. Use {@link #isCooldownActive(ServerPlayer, ToggleDirection)}.
     * The boolean parameter conflated direction with mode-name and led to caller bugs (see REVIEW 1.5).
     */
    @Deprecated(forRemoval = true)
    public static boolean isCooldownActive(ServerPlayer player, boolean wantPeace) {
        return isCooldownActive(player, wantPeace ? ToggleDirection.TO_PEACE : ToggleDirection.TO_COMBAT);
    }

    /**
     * Returns the remaining cooldown in milliseconds, or 0 if no cooldown.
     */
    public static long getCooldownRemainingMs(ServerPlayer player) {
        return CombatToggleData.get(player).getRemainingCooldownMs(nowTick(player));
    }
}
