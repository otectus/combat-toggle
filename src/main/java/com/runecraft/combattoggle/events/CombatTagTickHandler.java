package com.runecraft.combattoggle.events;

import com.runecraft.combattoggle.api.events.CombatTagExpiredEvent;
import com.runecraft.combattoggle.data.CombatToggleData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static com.runecraft.combattoggle.CombatToggle.MODID;

/**
 * Drives "your combat tag has expired" chat notifications without per-tick allocations.
 *
 * <p>The deadline tick for every actively-tagged player is cached here in-memory. Per-tick work is one
 * {@code map.entrySet().iterator()} walk plus a {@code currentTick > deadlineTick} comparison — no NBT
 * reads, no {@link CombatToggleData} allocations. Entries are pruned as players time out, log out, or
 * have their tag cleared by an admin.
 */
@Mod.EventBusSubscriber(modid = MODID)
public final class CombatTagTickHandler {

    private static final Map<UUID, Long> taggedDeadlinesTick = new HashMap<>();

    /** Records (or extends) the active deadline for {@code playerId} so the tick loop can fire the expiry chat. */
    public static void markTagged(UUID playerId, long deadlineTick) {
        taggedDeadlinesTick.merge(playerId, deadlineTick, Math::max);
    }

    /** Clears the tracked deadline (used on logout, /untag, and admin clear). */
    public static void markUntagged(UUID playerId) {
        taggedDeadlinesTick.remove(playerId);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || taggedDeadlinesTick.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long nowTick = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Long>> it = taggedDeadlinesTick.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (entry.getValue() > nowTick) continue;

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                CombatToggleData d = CombatToggleData.get(player);
                if (!d.isTagExpiryNotified()) {
                    player.sendSystemMessage(Component.translatable("combattoggle.msg.tag_expired"));
                    d.setTagExpiryNotified(true);
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new CombatTagExpiredEvent(player));
                }
            }
            it.remove();
        }
    }
}
