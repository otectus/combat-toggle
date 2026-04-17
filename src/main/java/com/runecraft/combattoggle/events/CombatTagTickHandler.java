package com.runecraft.combattoggle.events;

import com.runecraft.combattoggle.data.CombatToggleData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import static com.runecraft.combattoggle.CombatToggle.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public final class CombatTagTickHandler {

    private static final Set<UUID> taggedPlayers = new HashSet<>();

    public static void markTagged(UUID playerId) {
        taggedPlayers.add(playerId);
    }

    public static void markUntagged(UUID playerId) {
        taggedPlayers.remove(playerId);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (taggedPlayers.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long now = System.currentTimeMillis();
        Iterator<UUID> it = taggedPlayers.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                it.remove();
                continue;
            }
            CombatToggleData d = CombatToggleData.get(player);
            if (!d.isTagged(now)) {
                player.sendSystemMessage(Component.translatable("combattoggle.msg.tag_expired"));
                it.remove();
            }
        }
    }
}
