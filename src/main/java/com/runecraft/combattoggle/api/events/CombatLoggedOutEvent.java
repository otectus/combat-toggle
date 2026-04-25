package com.runecraft.combattoggle.api.events;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Posted on the Forge event bus when a player logs out. Carries the remaining combat-tag duration so
 * external combat-log penalty mods can apply consistent enforcement (kill-on-disconnect, item-drop,
 * Discord webhook, etc.) without re-implementing the tag bookkeeping.
 *
 * <p>{@link #isCombatLog()} returns true when the player disconnected with active tag time remaining
 * — the canonical "combat log" signal.
 */
public class CombatLoggedOutEvent extends PlayerEvent {
    private final long tagRemainingTicks;

    public CombatLoggedOutEvent(Player player, long tagRemainingTicks) {
        super(player);
        this.tagRemainingTicks = Math.max(0L, tagRemainingTicks);
    }

    /** Ticks left on the player's combat tag at the moment of disconnect; 0 if not tagged. */
    public long getTagRemainingTicks() {
        return tagRemainingTicks;
    }

    /** Convenience: true iff the player disconnected mid-tag (the canonical combat-log condition). */
    public boolean isCombatLog() {
        return tagRemainingTicks > 0;
    }
}
