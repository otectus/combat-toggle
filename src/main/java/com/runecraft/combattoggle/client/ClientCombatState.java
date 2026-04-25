package com.runecraft.combattoggle.client;

import net.minecraft.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.runecraft.combattoggle.CombatToggle.MODID;

public final class ClientCombatState {
    private static volatile boolean enabled = false;
    private static volatile long combatTagUntilMs = 0L;
    private static volatile long cooldownUntilMs = 0L;

    public static void update(boolean enabledIn, long combatTagRemainingMs, long cooldownRemainingMs) {
        enabled = enabledIn;
        long now = Util.getMillis();
        combatTagUntilMs = combatTagRemainingMs > 0 ? now + combatTagRemainingMs : 0L;
        cooldownUntilMs = cooldownRemainingMs > 0 ? now + cooldownRemainingMs : 0L;
    }

    /** Zero all client-cached state. Called on disconnect/connect to prevent stale HUD across servers. */
    public static void reset() {
        enabled = false;
        combatTagUntilMs = 0L;
        cooldownUntilMs = 0L;
    }

    public static boolean isEnabled() { return enabled; }
    public static long getCombatTagUntilMs() { return combatTagUntilMs; }
    public static long getCooldownUntilMs() { return cooldownUntilMs; }
}

/** Resets {@link ClientCombatState} on connect/disconnect so HUD never carries stale state across servers. */
@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
final class ClientNetworkLifecycle {
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn e) {
        ClientCombatState.reset();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientCombatState.reset();
    }
}
