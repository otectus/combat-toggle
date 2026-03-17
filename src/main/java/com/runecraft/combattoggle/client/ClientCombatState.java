package com.runecraft.combattoggle.client;

public final class ClientCombatState {
    private static volatile boolean enabled = false;
    private static volatile long lastToggleMs = 0L;
    private static volatile long combatTagUntilMs = 0L;

    public static void update(boolean enabledIn, long lastToggleMsIn, long combatTagUntilMsIn) {
        enabled = enabledIn;
        lastToggleMs = lastToggleMsIn;
        combatTagUntilMs = combatTagUntilMsIn;
    }

    public static boolean isEnabled() { return enabled; }
    public static long getLastToggleMs() { return lastToggleMs; }
    public static long getCombatTagUntilMs() { return combatTagUntilMs; }
}
