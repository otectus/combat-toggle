package com.runecraft.combattoggle.common.data;

import com.runecraft.combattoggle.common.CTConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Per-player Combat Toggle state, stored as a Forge capability on every {@link net.minecraft.world.entity.player.Player}.
 *
 * <p>Time-based fields are persisted as <em>game ticks</em> ({@code level.getGameTime()}) — monotonic,
 * persists to disk, advances at exactly 20 Hz, and survives server restarts. The HUD-bound packet
 * still carries millisecond remainders for client-side display, computed as {@code ticks * 50}.
 *
 * <p>{@link #get(ServerPlayer)} returns the same instance for the lifetime of that player entity
 * (no per-call allocation). Mutations apply directly; persistence is handled by the capability framework.
 */
public final class CombatToggleData implements INBTSerializable<CompoundTag> {
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LAST_TOGGLE_TICK = "last_toggle_tick";
    private static final String KEY_COMBAT_TAG_UNTIL_TICK = "combat_tag_until_tick";
    private static final String KEY_LAST_PVP_TICK = "last_pvp_tick";
    private static final String KEY_TAG_EXPIRY_NOTIFIED = "tag_expiry_notified";

    public static final long TICKS_PER_SECOND = 20L;
    public static final long MS_PER_TICK = 50L;

    private boolean enabled;
    private long lastToggleTick;
    private long combatTagUntilTick;
    private long lastPvpTick;
    private boolean tagExpiryNotified;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }

    public long getLastToggleTick() { return lastToggleTick; }
    public void setLastToggleTick(long v) { this.lastToggleTick = v; }

    public long getCombatTagUntilTick() { return combatTagUntilTick; }
    public void setCombatTagUntilTick(long v) {
        this.combatTagUntilTick = v;
        this.tagExpiryNotified = false;
    }

    public long getLastPvpTick() { return lastPvpTick; }
    public void setLastPvpTick(long v) { this.lastPvpTick = v; }

    public boolean isTagExpiryNotified() { return tagExpiryNotified; }
    public void setTagExpiryNotified(boolean v) { this.tagExpiryNotified = v; }

    public static CombatToggleData get(ServerPlayer player) {
        return player.getCapability(CombatToggleCapability.CAPABILITY)
                .orElseThrow(() -> new IllegalStateException("CombatToggle capability missing on " + player.getScoreboardName()));
    }

    public boolean isTagged(long nowTick) {
        return combatTagUntilTick > nowTick;
    }

    public long getCombatTagRemainingMs(long nowTick) {
        long ticksLeft = combatTagUntilTick - nowTick;
        return ticksLeft > 0 ? ticksLeft * MS_PER_TICK : 0L;
    }

    /**
     * Extends (never shortens) the active combat tag by the configured duration. No-op if
     * {@code combatTagSeconds <= 0}.
     */
    public void applyCombatTag(long nowTick) {
        int tagSeconds = CTConfig.combatTagSeconds.get();
        if (tagSeconds <= 0) return;
        long until = nowTick + (tagSeconds * TICKS_PER_SECOND);
        if (until > combatTagUntilTick) {
            combatTagUntilTick = until;
            tagExpiryNotified = false;
        }
    }

    /** PvP-allowed rule: if {@code requireBothCombatEnabled}, both must be Combat; else attacker-only. */
    public static boolean isPvpAllowed(CombatToggleData attacker, CombatToggleData victim) {
        boolean requireBoth = CTConfig.requireBothCombatEnabled.get();
        return requireBoth ? (attacker.isEnabled() && victim.isEnabled()) : attacker.isEnabled();
    }

    public static boolean isPvpAllowed(ServerPlayer attacker, ServerPlayer victim) {
        return isPvpAllowed(get(attacker), get(victim));
    }

    public enum CooldownSource { NONE, TOGGLE, PVP }

    public long getRemainingCooldownTicks(long nowTick) {
        int cooldownSec = CTConfig.cooldownSeconds.get();
        if (cooldownSec <= 0) return 0L;
        long cooldownTicks = cooldownSec * TICKS_PER_SECOND;
        long toggleRem = CTConfig.cooldownTriggersOnToggle.get()
                ? Math.max(0, cooldownTicks - (nowTick - lastToggleTick)) : 0;
        long pvpRem = CTConfig.cooldownTriggersOnPvp.get()
                ? Math.max(0, cooldownTicks - (nowTick - lastPvpTick)) : 0;
        return Math.max(toggleRem, pvpRem);
    }

    public long getRemainingCooldownMs(long nowTick) {
        return getRemainingCooldownTicks(nowTick) * MS_PER_TICK;
    }

    public CooldownSource getDominantCooldownSource(long nowTick) {
        int cooldownSec = CTConfig.cooldownSeconds.get();
        if (cooldownSec <= 0) return CooldownSource.NONE;
        long cooldownTicks = cooldownSec * TICKS_PER_SECOND;
        long toggleRem = CTConfig.cooldownTriggersOnToggle.get()
                ? Math.max(0, cooldownTicks - (nowTick - lastToggleTick)) : 0;
        long pvpRem = CTConfig.cooldownTriggersOnPvp.get()
                ? Math.max(0, cooldownTicks - (nowTick - lastPvpTick)) : 0;
        if (toggleRem <= 0 && pvpRem <= 0) return CooldownSource.NONE;
        return pvpRem >= toggleRem ? CooldownSource.PVP : CooldownSource.TOGGLE;
    }

    /**
     * Whether the cooldown should currently block a toggle in the given direction. The 1.1.0 boolean
     * {@code cooldownAppliesToPeaceOnly} is preserved verbatim here: when true, the cooldown only
     * blocks Combat -> Peace transitions; switching to Combat is always allowed.
     *
     * @param wantCombat the mode the player is trying to switch to
     */
    public boolean isCooldownActiveForDirection(long nowTick, boolean wantCombat) {
        if (getRemainingCooldownTicks(nowTick) <= 0) return false;
        if (CTConfig.cooldownAppliesToPeaceOnly.get()) {
            // Block only Combat -> Peace, i.e. blocked iff player is trying to enter Peace.
            return !wantCombat;
        }
        return true;
    }

    /** Used by the capability clone hook on respawn. */
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
        enabled = tag.contains(KEY_ENABLED) && tag.getBoolean(KEY_ENABLED);
        lastToggleTick = tag.getLong(KEY_LAST_TOGGLE_TICK);
        combatTagUntilTick = tag.getLong(KEY_COMBAT_TAG_UNTIL_TICK);
        lastPvpTick = tag.getLong(KEY_LAST_PVP_TICK);
        tagExpiryNotified = tag.getBoolean(KEY_TAG_EXPIRY_NOTIFIED);
    }
}
