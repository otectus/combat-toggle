package com.runecraft.combattoggle.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client state sync. Carries the player's mode plus countdown remainders for the HUD.
 *
 * <p>Wire format: {@code boolean enabled | varInt tagRemainingSec | varInt cooldownRemainingSec}.
 * The HUD only displays mm:ss, so sub-second precision is wasted. ~5 bytes total.
 *
 * <p>Side-safety: the {@link #handle} method references {@code ClientCombatState} only inside the
 * {@code enqueueWork} lambda body. The JVM does not load that class until the lambda actually
 * executes — and S2C lambdas only fire on the physical client. Dedicated servers register this
 * packet for encoding (to send) but never run the handler, so {@code ClientCombatState} stays
 * unloaded there.
 */
public final class S2CSyncStatePacket {
    public final boolean enabled;
    public final long combatTagRemainingMs;
    public final long cooldownRemainingMs;

    public S2CSyncStatePacket(boolean enabled, long combatTagRemainingMs, long cooldownRemainingMs) {
        this.enabled = enabled;
        this.combatTagRemainingMs = combatTagRemainingMs;
        this.cooldownRemainingMs = cooldownRemainingMs;
    }

    public static void encode(S2CSyncStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
        buf.writeVarInt(msToSecondsCeil(msg.combatTagRemainingMs));
        buf.writeVarInt(msToSecondsCeil(msg.cooldownRemainingMs));
    }

    public static S2CSyncStatePacket decode(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        long tagMs = buf.readVarInt() * 1000L;
        long cdMs = buf.readVarInt() * 1000L;
        return new S2CSyncStatePacket(enabled, tagMs, cdMs);
    }

    public static void handle(S2CSyncStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() ->
                com.runecraft.combattoggle.client.ClientCombatState.update(
                        msg.enabled, msg.combatTagRemainingMs, msg.cooldownRemainingMs));
        c.setPacketHandled(true);
    }

    /** Round up so an in-progress 1500 ms remainder doesn't get sent as 1 sec and look "early" on the HUD. */
    private static int msToSecondsCeil(long ms) {
        if (ms <= 0L) return 0;
        long sec = (ms + 999L) / 1000L;
        return sec > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sec;
    }
}
