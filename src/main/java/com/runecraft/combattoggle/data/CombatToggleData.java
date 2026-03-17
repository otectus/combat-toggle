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

    public boolean enabled;
    public long lastToggleMs;
    public long combatTagUntilMs;
    public long lastPvpMs;

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
        int cooldownSec = CTConfig.cooldownSeconds.get();
        if (cooldownSec <= 0) return false;

        long cooldownMs = cooldownSec * 1000L;
        boolean cooldownActive = false;

        // Check toggle-based cooldown
        if (CTConfig.cooldownTriggersOnToggle.get()) {
            if ((nowMs - lastToggleMs) < cooldownMs) {
                cooldownActive = true;
            }
        }

        // Check PvP-based cooldown
        if (CTConfig.cooldownTriggersOnPvp.get()) {
            if ((nowMs - lastPvpMs) < cooldownMs) {
                cooldownActive = true;
            }
        }

        // If cooldown only applies to Peace mode, allow Combat mode toggle
        if (cooldownActive && CTConfig.cooldownAppliesToPeaceOnly.get()) {
            return wantPeace; // Only block if trying to go to Peace
        }

        return cooldownActive;
    }

    public long getRemainingCooldown(long nowMs) {
        int cooldownSec = CTConfig.cooldownSeconds.get();
        if (cooldownSec <= 0) return 0;

        long cooldownMs = cooldownSec * 1000L;
        long remaining = 0;

        // Check both cooldown sources and return the longest
        if (CTConfig.cooldownTriggersOnToggle.get()) {
            long toggleRemaining = cooldownMs - (nowMs - lastToggleMs);
            if (toggleRemaining > remaining) remaining = toggleRemaining;
        }

        if (CTConfig.cooldownTriggersOnPvp.get()) {
            long pvpRemaining = cooldownMs - (nowMs - lastPvpMs);
            if (pvpRemaining > remaining) remaining = pvpRemaining;
        }

        return Math.max(0, remaining);
    }
}
