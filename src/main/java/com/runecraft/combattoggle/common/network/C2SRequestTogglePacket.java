package com.runecraft.combattoggle.common.network;

import com.runecraft.combattoggle.server.ToggleService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-keybind path. The vanilla-fallback path goes through {@code /ct} which calls
 * {@link ToggleService#requestToggle} directly, so this packet only exists for modded clients.
 *
 * <p>Empty payload: the request is identified by the sender ({@code ctx.getSender()}).
 */
public final class C2SRequestTogglePacket {

    public C2SRequestTogglePacket() {}

    public static void encode(C2SRequestTogglePacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static C2SRequestTogglePacket decode(FriendlyByteBuf buf) {
        return new C2SRequestTogglePacket();
    }

    public static void handle(C2SRequestTogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer p = c.getSender();
            if (p == null) return;
            ToggleService.requestToggleFromKeybind(p);
        });
        c.setPacketHandled(true);
    }
}
