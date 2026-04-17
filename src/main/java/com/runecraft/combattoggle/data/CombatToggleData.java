package com.runecraft.combattoggle.data;

import com.runecraft.combattoggle.config.CTConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public final class CombatToggleData {
    private static final String ROOT = "combat_toggle";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LAST_TOGGLE_MS = "last_toggle_ms";
    private static final String KEY_COMBAT_TAG_UNTIL_MS = "combat_tag_until_ms";
    private static final String KEY_LAST_PVP_MS = "last_pvp_ms";

    private boolean enabled;
    private long lastToggleMs;
    private long combatTagUntilMs;
    private long lastPvpMs;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getLastToggleMs() { return lastToggleMs; }
    public void setLastToggleMs(long lastToggleMs) { this.lastToggleMs = lastToggleMs; }

    public long getCombatTagUntilMs() { return combatTagUntilMs; }
    public void setCombatTagUntilMs(long combatTagUntilMs) { this.combatTagUntilMs = combatTagUntilMs; }

    public long getLastPvpMs() { return lastPvpMs; }
    public void setLastPvpMs(long lastPvpMs) { this.lastPvpMs = lastPvpMs; }

    public static CombatToggleData get(ServerPlayer player) {
        CompoundTag p = player.getPersistentData();
        CompoundTag root = p.getCompound(ROOT);

        CombatToggleData d = new CombatToggleData();
        d.enabled = root.contains(KEY_ENABLED) ? root.getBoolean(KEY_ENABLED) : false; // default Peace
        d.lastToggleMs = root.getLong(KEY_LAST_TOGGLE_MS);
        d.combatTagUntilMs = root.getLong(KEY_COMBAT_TAG_UNTIL_MS);
        d.lastPvpMs = root.getLong(KEY_LAST_PVP_MS);

        return d;
    }

    public void save(ServerPlayer player) {
        CompoundTag p = player.getPersistentData();
        CompoundTag root = p.getCompound(ROOT);

        root.putBoolean(KEY_ENABLED, enabled);
        root.putLong(KEY_LAST_TOGGLE_MS, lastToggleMs);
        root.putLong(KEY_COMBAT_TAG_UNTIL_MS, combatTagUntilMs);
        root.putLong(KEY_LAST_PVP_MS, lastPvpMs);

        p.put(ROOT, root);
    }

    public boolean isTagged(long nowMs) {
        return combatTagUntilMs > nowMs;
    }

    public void applyCombatTag(long nowMs) {
        int tagSeconds = CTConfig.combatTagSeconds.get();
        if (tagSeconds <= 0) return;

        long until = nowMs + (tagSeconds * 1000L);
        if (until > combatTagUntilMs) combatTagUntilMs = until;
    }

    public boolean isCooldownActive(long nowMs, boolean wantPeace) {
        CooldownState cs = resolveCooldown(nowMs);
        boolean active = cs.toggleRemainingMs() > 0 || cs.pvpRemainingMs() > 0;
        if (active && CTConfig.cooldownAppliesToPeaceOnly.get()) {
            return wantPeace; // cooldown only blocks Combat->Peace direction
        }
        return active;
    }

    public enum CooldownSource { NONE, TOGGLE, PVP }

    /** Snapshot of per-source cooldown remainders. Fields are clamped to >=0. */
    public record CooldownState(long toggleRemainingMs, long pvpRemainingMs) {
        public long dominantRemainingMs() { return Math.max(toggleRemainingMs, pvpRemainingMs); }
        public CooldownSource dominantSource() {
            if (toggleRemainingMs <= 0 && pvpRemainingMs <= 0) return CooldownSource.NONE;
            return pvpRemainingMs >= toggleRemainingMs ? CooldownSource.PVP : CooldownSource.TOGGLE;
        }
    }

    public CooldownState resolveCooldown(long nowMs) {
        int cooldownSec = CTConfig.cooldownSeconds.get();
        if (cooldownSec <= 0) return new CooldownState(0, 0);
        long cooldownMs = cooldownSec * 1000L;
        long toggleRem = CTConfig.cooldownTriggersOnToggle.get()
                ? Math.max(0, cooldownMs - (nowMs - lastToggleMs)) : 0;
        long pvpRem = CTConfig.cooldownTriggersOnPvp.get()
                ? Math.max(0, cooldownMs - (nowMs - lastPvpMs)) : 0;
        return new CooldownState(toggleRem, pvpRem);
    }

    public CooldownSource getDominantCooldownSource(long nowMs) {
        return resolveCooldown(nowMs).dominantSource();
    }

    public long getRemainingCooldown(long nowMs) {
        return resolveCooldown(nowMs).dominantRemainingMs();
    }
}
