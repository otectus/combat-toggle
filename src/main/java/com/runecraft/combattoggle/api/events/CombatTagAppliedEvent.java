package com.runecraft.combattoggle.api.events;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Posted on the Forge event bus after a player is freshly combat-tagged or has an existing tag extended.
 * Not cancelable — the tag has already been applied.
 *
 * <p>External tooling (e.g. combat-log penalty mods) can subscribe to know exactly when a player became
 * tag-bound and how long the tag will last.
 */
public class CombatTagAppliedEvent extends PlayerEvent {
    private final long durationTicks;
    private final long deadlineTick;

    public CombatTagAppliedEvent(Player player, long durationTicks, long deadlineTick) {
        super(player);
        this.durationTicks = durationTicks;
        this.deadlineTick = deadlineTick;
    }

    /** Tag duration in ticks (50 ms per tick). */
    public long getDurationTicks() {
        return durationTicks;
    }

    /** Game-time tick at which the tag will expire. */
    public long getDeadlineTick() {
        return deadlineTick;
    }
}
