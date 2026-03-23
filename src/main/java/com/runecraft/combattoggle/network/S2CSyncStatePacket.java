package com.runecraft.combattoggle.network;

import com.runecraft.combattoggle.client.ClientCombatState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

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
        buf.writeLong(msg.combatTagRemainingMs);
        buf.writeLong(msg.cooldownRemainingMs);
    }

    public static S2CSyncStatePacket decode(FriendlyByteBuf buf) {
        return new S2CSyncStatePacket(buf.readBoolean(), buf.readLong(), buf.readLong());
    }

    public static void handle(S2CSyncStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> ClientCombatState.update(msg.enabled, msg.combatTagRemainingMs, msg.cooldownRemainingMs));
        c.setPacketHandled(true);
    }
}
