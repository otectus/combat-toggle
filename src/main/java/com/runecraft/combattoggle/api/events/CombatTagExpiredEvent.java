package com.runecraft.combattoggle.api.events;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Posted on the Forge event bus when a player's combat tag elapses naturally.
 * Fired both from the per-tick expiry walker and from the login-side notification path
 * (when the tag elapsed while the player was offline). Not cancelable.
 */
public class CombatTagExpiredEvent extends PlayerEvent {
    public CombatTagExpiredEvent(Player player) {
        super(player);
    }
}
