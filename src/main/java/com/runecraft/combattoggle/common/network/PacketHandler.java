package com.runecraft.combattoggle.common.network;

import com.runecraft.combattoggle.CombatToggle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Server-side-installable network channel.
 *
 * <p>Critical: {@link NetworkRegistry#acceptMissingOr(java.util.function.Predicate)} on both sides
 * means the handshake passes if the peer either (a) doesn't have the channel registered (vanilla)
 * or (b) has the same protocol version. Vanilla clients can connect; mismatched-version modded
 * clients get a clean handshake rejection.
 *
 * <p>Combined with {@code displayTest="IGNORE_SERVER_VERSION"} in mods.toml, this is the entire
 * "server-installable optional client" pattern for Forge 1.19.2.
 */
public final class PacketHandler {
    private static final String PROTOCOL = "1";
    private static int id = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CombatToggle.MODID, "main"),
            () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL::equals),
            NetworkRegistry.acceptMissingOr(PROTOCOL::equals)
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

    /**
     * Sends a packet to a player. If the client doesn't have the mod (vanilla), the SimpleChannel
     * silently drops the message — never throws. Server-side toggle paths can call this
     * unconditionally without checking client modness.
     */
    public static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    private PacketHandler() {}
}
