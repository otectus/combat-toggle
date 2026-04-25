package com.runecraft.combattoggle.data;

import com.runecraft.combattoggle.config.CTConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Per-player Combat Toggle state, stored as a Forge capability on the {@link net.minecraft.world.entity.player.Player}.
 *
 * <p>Time-based fields are stored as <em>game ticks</em> (overworld {@code getGameTime()}). Game time is monotonic,
 * persists to disk, advances at exactly 20 Hz, and is the canonical clock for all in-game timers — so deadlines
 * survive server restarts correctly, unlike wall-clock millis (NTP steps, manual clock changes) or
 * {@code Util.getMillis()} (resets at JVM start). The HUD packet still carries millisecond remainders for
 * client-side display (computed as {@code ticksRemaining * 50}).
 *
 * <p>This object lives on the player itself: {@code CombatToggleData.get(player)} returns the same instance
 * for the lifetime of that player entity, with no per-call allocation. Mutations apply directly; persistence
 * is handled automatically by the capability framework.
 */
public final class CombatToggleData implements INBTSerializable<CompoundTag> {
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LAST_TOGGLE_TICK = "last_toggle_tick";
    private static final String KEY_COMBAT_TAG_UNTIL_TICK = "combat_tag_until_tick";
    private static final String KEY_LAST_PVP_TICK = "last_pvp_tick";
    private static final String KEY_TAG_EXPIRY_NOTIFIED = "tag_expiry_notified";

    /** 1 second = 20 ticks. */
    public static final long TICKS_PER_SECOND = 20L;
    /** 1 tick = 50 ms. */
    public static final long MS_PER_TICK = 50L;

    private boolean enabled;
    private long lastToggleTick;
    private long combatTagUntilTick;
    private long lastPvpTick;
    private boolean tagExpiryNotified;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getLastToggleTick() { return lastToggleTick; }
    public void setLastToggleTick(long lastToggleTick) { this.lastToggleTick = lastToggleTick; }

    public long getCombatTagUntilTick() { return combatTagUntilTick; }
    public void setCombatTagUntilTick(long combatTagUntilTick) {
        this.combatTagUntilTick = combatTagUntilTick;
        this.tagExpiryNotified = false;
    }

    public long getLastPvpTick() { return lastPvpTick; }
    public void setLastPvpTick(long lastPvpTick) { this.lastPvpTick = lastPvpTick; }

    public boolean isTagExpiryNotified() { return tagExpiryNotified; }
    public void setTagExpiryNotified(boolean v) { this.tagExpiryNotified = v; }

    /**
     * Returns the live capability-backed instance for this player.
     * Allocation happens once at attach time; subsequent calls return the same object.
     */
    public static CombatToggleData get(ServerPlayer player) {
        return player.getCapability(CombatToggleCapability.CAPABILITY)
                .orElseThrow(() -> new IllegalStateException("CombatToggle capability missing on " + player.getScoreboardName()));
    }

    /**
     * Retained as a no-op for source compatibility with pre-1.2.0 call sites. The capability framework
     * auto-persists the underlying state when the player is saved.
     */
    public void save(ServerPlayer player) {
        // intentionally empty
    }

    public boolean isTagged(long nowTick) {
        return combatTagUntilTick > nowTick;
    }

    /** Remaining tag duration in milliseconds, suitable for HUD packets; 0 if not tagged. */
    public long getCombatTagRemainingMs(long nowTick) {
        long ticksLeft = combatTagUntilTick - nowTick;
        return ticksLeft > 0 ? ticksLeft * MS_PER_TICK : 0L;
    }

    public void applyCombatTag(long nowTick) {
        int tagSeconds = CTConfig.combatTagSeconds.get();
        if (tagSeconds <= 0) return;

        long until = nowTick + (tagSeconds * TICKS_PER_SECOND);
        if (until > combatTagUntilTick) {
            combatTagUntilTick = until;
            tagExpiryNotified = false;
        }
    }

    /**
     * Single source of truth for the PvP-allowed rule, used by both enforcement and the public API.
     */
    public static boolean isPvpAllowed(CombatToggleData attacker, CombatToggleData victim) {
        boolean requireBoth = CTConfig.requireBothCombatEnabled.get();
        return requireBoth ? (attacker.isEnabled() && victim.isEnabled()) : attacker.isEnabled();
    }

    public static boolean isPvpAllowed(ServerPlayer attacker, ServerPlayer victim) {
        return isPvpAllowed(get(attacker), get(victim));
    }

    /**
     * Returns true if the configured cooldown is currently active <em>and</em> would block a transition
     * in the given direction, per {@link CTConfig#cooldownScope}.
     */
    public boolean isCooldownActiveForDirection(long nowTick, ToggleDirection direction) {
        CooldownState cs = resolveCooldown(nowTick);
        boolean active = cs.toggleRemainingTicks() > 0 || cs.pvpRemainingTicks() > 0;
        if (!active) return false;
        return switch (CTConfig.cooldownScope.get()) {
            case BOTH -> true;
            case NONE -> false;
            case PEACE_ONLY -> direction == ToggleDirection.TO_PEACE;
            case COMBAT_ONLY -> direction == ToggleDirection.TO_COMBAT;
        };
    }

    public enum CooldownSource { NONE, TOGGLE, PVP }

    /** Snapshot of per-source cooldown remainders in ticks. Fields are clamped to {@code >= 0}. */
    public record CooldownState(long toggleRemainingTicks, long pvpRemainingTicks) {
        public long dominantRemainingTicks() { return Math.max(toggleRemainingTicks, pvpRemainingTicks); }
        public long dominantRemainingMs() { return dominantRemainingTicks() * MS_PER_TICK; }
        public CooldownSource dominantSource() {
            if (toggleRemainingTicks <= 0 && pvpRemainingTicks <= 0) return CooldownSource.NONE;
            return pvpRemainingTicks >= toggleRemainingTicks ? CooldownSource.PVP : CooldownSource.TOGGLE;
        }
    }

    public CooldownState resolveCooldown(long nowTick) {
        int cooldownSec = CTConfig.cooldownSeconds.get();
        if (cooldownSec <= 0) return new CooldownState(0, 0);
        long cooldownTicks = cooldownSec * TICKS_PER_SECOND;
        long toggleRem = CTConfig.cooldownTriggersOnToggle.get()
                ? Math.max(0, cooldownTicks - (nowTick - lastToggleTick)) : 0;
        long pvpRem = CTConfig.cooldownTriggersOnPvp.get()
                ? Math.max(0, cooldownTicks - (nowTick - lastPvpTick)) : 0;
        return new CooldownState(toggleRem, pvpRem);
    }

    public CooldownSource getDominantCooldownSource(long nowTick) {
        return resolveCooldown(nowTick).dominantSource();
    }

    /** Cooldown remaining in ticks, picking the longer of the two cooldown sources. */
    public long getRemainingCooldownTicks(long nowTick) {
        return resolveCooldown(nowTick).dominantRemainingTicks();
    }

    /** Cooldown remaining in milliseconds, suitable for HUD packets. */
    public long getRemainingCooldownMs(long nowTick) {
        return resolveCooldown(nowTick).dominantRemainingMs();
    }

    /** Copies all state from another instance. Used by the capability clone hook on respawn. */
    public void copyFrom(CombatToggleData other) {
        this.enabled = other.enabled;
        this.lastToggleTick = other.lastToggleTick;
        this.combatTagUntilTick = other.combatTagUntilTick;
        this.lastPvpTick = other.lastPvpTick;
        this.tagExpiryNotified = other.tagExpiryNotified;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_ENABLED, enabled);
        tag.putLong(KEY_LAST_TOGGLE_TICK, lastToggleTick);
        tag.putLong(KEY_COMBAT_TAG_UNTIL_TICK, combatTagUntilTick);
        tag.putLong(KEY_LAST_PVP_TICK, lastPvpTick);
        tag.putBoolean(KEY_TAG_EXPIRY_NOTIFIED, tagExpiryNotified);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        enabled = tag.contains(KEY_ENABLED) ? tag.getBoolean(KEY_ENABLED) : false;
        lastToggleTick = tag.getLong(KEY_LAST_TOGGLE_TICK);
        combatTagUntilTick = tag.getLong(KEY_COMBAT_TAG_UNTIL_TICK);
        lastPvpTick = tag.getLong(KEY_LAST_PVP_TICK);
        tagExpiryNotified = tag.getBoolean(KEY_TAG_EXPIRY_NOTIFIED);
    }
}
