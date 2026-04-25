package com.runecraft.combattoggle.network;

import com.runecraft.combattoggle.client.ClientCombatState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client state sync. Carries the player's mode plus countdown remainders for the HUD.
 *
 * <p>Wire format (protocol v3): {@code boolean enabled | varint tagRemainingSec | varint cooldownRemainingSec}.
 * The HUD only renders mm:ss, so sub-second precision is wasted. Two varints (typically 2 bytes each for
 * sub-hour deadlines) plus the boolean total ~5 bytes — down from the 17 bytes the v2 packet used.
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
        c.enqueueWork(() -> ClientCombatState.update(msg.enabled, msg.combatTagRemainingMs, msg.cooldownRemainingMs));
        c.setPacketHandled(true);
    }

    /** Round up so an in-progress 1500 ms remainder doesn't get sent as 1 sec and look "early" on the HUD. */
    private static int msToSecondsCeil(long ms) {
        if (ms <= 0L) return 0;
        long sec = (ms + 999L) / 1000L;
        // VarInt is 32-bit; clamp the absurd case (would only happen if config is set to ~68 years).
        return sec > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sec;
    }
}
