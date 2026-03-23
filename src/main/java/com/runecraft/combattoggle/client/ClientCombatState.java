package com.runecraft.combattoggle.client;

public final class ClientCombatState {
    private static volatile boolean enabled = false;
    private static volatile long combatTagUntilMs = 0L;
    private static volatile long cooldownUntilMs = 0L;

    public static void update(boolean enabledIn, long combatTagRemainingMs, long cooldownRemainingMs) {
        enabled = enabledIn;
        long now = System.currentTimeMillis();
        combatTagUntilMs = combatTagRemainingMs > 0 ? now + combatTagRemainingMs : 0L;
        cooldownUntilMs = cooldownRemainingMs > 0 ? now + cooldownRemainingMs : 0L;
    }

    public static boolean isEnabled() { return enabled; }
    public static long getCombatTagUntilMs() { return combatTagUntilMs; }
    public static long getCooldownUntilMs() { return cooldownUntilMs; }
}
