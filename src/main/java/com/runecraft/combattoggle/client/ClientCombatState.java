package com.runecraft.combattoggle.client;

import net.minecraft.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.runecraft.combattoggle.CombatToggle.MODID;

/**
 * Client-side cache populated by {@link com.runecraft.combattoggle.common.network.S2CSyncStatePacket}.
 * Vanilla clients never receive sync packets, so this class is only consulted when the client mod
 * is present — and is therefore safely Dist.CLIENT-only.
 *
 * <p>Server-side remainders are translated into client-local absolute deadlines using
 * {@link Util#getMillis()} (monotonic; correct here because the deadlines are read again only
 * within the same client-runtime session).
 */
public final class ClientCombatState {
    private static volatile boolean enabled = false;
    private static volatile long combatTagUntilMs = 0L;
    private static volatile long cooldownUntilMs = 0L;

    private ClientCombatState() {}

    public static void update(boolean enabledIn, long tagRemainingMs, long cooldownRemainingMs) {
        enabled = enabledIn;
        long now = Util.getMillis();
        combatTagUntilMs = tagRemainingMs > 0 ? now + tagRemainingMs : 0L;
        cooldownUntilMs = cooldownRemainingMs > 0 ? now + cooldownRemainingMs : 0L;
    }

    public static void reset() {
        enabled = false;
        combatTagUntilMs = 0L;
        cooldownUntilMs = 0L;
    }

    public static boolean isEnabled() { return enabled; }
    public static long getCombatTagUntilMs() { return combatTagUntilMs; }
    public static long getCooldownUntilMs() { return cooldownUntilMs; }
}

/** Resets {@link ClientCombatState} on connect/disconnect so the HUD never carries stale state across servers. */
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
