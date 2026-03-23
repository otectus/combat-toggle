package com.runecraft.combattoggle.events;

import com.runecraft.combattoggle.data.CombatToggleData;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.network.PacketHandler;
import com.runecraft.combattoggle.network.S2CSyncStatePacket;
import com.runecraft.combattoggle.util.TeamManager;
import com.runecraft.combattoggle.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.runecraft.combattoggle.CombatToggle.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public final class PlayerLifecycleEvents {

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)) return;

        // Keep persistentData automatically exists on player, but copying keys is safer for modded edge cases
        newPlayer.getPersistentData().merge(oldPlayer.getPersistentData());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        syncAndEnforce(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        syncAndEnforce(p);
    }

    private static void syncAndEnforce(ServerPlayer p) {
        long now = System.currentTimeMillis();
        CombatToggleData d = CombatToggleData.get(p);

        if (d.isTagged(now) && CTConfig.forceCombatWhileTagged.get()) {
            if (!d.enabled) {
                d.enabled = true;
                d.save(p);
                p.sendSystemMessage(TextUtil.system("[Combat Toggle] Combat tag active, forcing Combat mode. Remaining: " + TextUtil.formatRemaining(d.combatTagUntilMs - now)));
            }
        }

        // Update scoreboard team for nameplate color
        TeamManager.updatePlayerTeam(p, d.enabled);

        PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.enabled, Math.max(0, d.combatTagUntilMs - now), d.getRemainingCooldown(now)));
    }
}
