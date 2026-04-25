package com.runecraft.combattoggle.api.events;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Posted on the Forge event bus immediately before a Combat Toggle mode change is persisted.
 * Cancelable: a listener that cancels prevents the mode flip.
 *
 * <p>Other mods can use this to gate transitions (e.g. force Peace inside a safe-zone region).
 */
@Cancelable
public class CombatToggleStateChangeEvent extends PlayerEvent {
    /** Why the mode change was attempted. */
    public enum Reason {
        /** A player pressed the toggle keybind. */
        PLAYER_TOGGLE,
        /** An admin invoked {@code /combattoggle set}. */
        ADMIN_SET,
        /** {@code forceCombatWhileTagged} flipped a Peace player to Combat after a PvP hit or login while tagged. */
        FORCE_COMBAT_WHILE_TAGGED
    }

    private final boolean newCombatState;
    private final Reason reason;

    public CombatToggleStateChangeEvent(Player player, boolean newCombatState, Reason reason) {
        super(player);
        this.newCombatState = newCombatState;
        this.reason = reason;
    }

    /** True if the player is about to enter Combat mode; false if entering Peace. */
    public boolean isNewCombatState() {
        return newCombatState;
    }

    public Reason getReason() {
        return reason;
    }
}
