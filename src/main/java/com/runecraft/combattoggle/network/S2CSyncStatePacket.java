package com.runecraft.combattoggle.network;

import com.runecraft.combattoggle.client.ClientCombatState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class S2CSyncStatePacket {
    public final boolean enabled;
    public final long lastToggleMs;
    public final long combatTagUntilMs;

    public S2CSyncStatePacket(boolean enabled, long lastToggleMs, long combatTagUntilMs) {
        this.enabled = enabled;
        this.lastToggleMs = lastToggleMs;
        this.combatTagUntilMs = combatTagUntilMs;
    }

    public static void encode(S2CSyncStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
        buf.writeLong(msg.lastToggleMs);
        buf.writeLong(msg.combatTagUntilMs);
    }

    public static S2CSyncStatePacket decode(FriendlyByteBuf buf) {
        return new S2CSyncStatePacket(buf.readBoolean(), buf.readLong(), buf.readLong());
    }

    public static void handle(S2CSyncStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> ClientCombatState.update(msg.enabled, msg.lastToggleMs, msg.combatTagUntilMs));
        c.setPacketHandled(true);
    }
}
