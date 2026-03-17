package com.runecraft.combattoggle.network;

import com.runecraft.combattoggle.CombatToggle;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PacketHandler {
    private static final String PROTOCOL = "1";
    private static int id = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CombatToggle.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    public static void register() {
        CHANNEL.messageBuilder(C2SRequestTogglePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SRequestTogglePacket::encode)
                .decoder(C2SRequestTogglePacket::decode)
                .consumerMainThread(C2SRequestTogglePacket::handle)
                .add();
        
        CHANNEL.messageBuilder(S2CSyncStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSyncStatePacket::encode)
                .decoder(S2CSyncStatePacket::decode)
                .consumerMainThread(S2CSyncStatePacket::handle)
                .add();
    }

    public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
