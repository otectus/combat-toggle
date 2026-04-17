package com.runecraft.combattoggle.events;

import com.runecraft.combattoggle.data.CombatToggleData;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.network.C2SRequestTogglePacket;
import com.runecraft.combattoggle.network.PacketHandler;
import com.runecraft.combattoggle.network.S2CSyncStatePacket;
import com.runecraft.combattoggle.util.TeamManager;
import com.runecraft.combattoggle.util.TextUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.runecraft.combattoggle.CombatToggle.LOGGER;
import static com.runecraft.combattoggle.CombatToggle.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public final class PlayerLifecycleEvents {

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)) return;

        // Copy only our mod's data compound to avoid interfering with other mods
        CompoundTag oldData = oldPlayer.getPersistentData().getCompound("combat_toggle");
        if (!oldData.isEmpty()) {
            newPlayer.getPersistentData().put("combat_toggle", oldData.copy());
        }
        LOGGER.debug("Player {} cloned, copying combat_toggle data", newPlayer.getScoreboardName());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;

        TeamManager.removePlayerFromTeams(p);
        C2SRequestTogglePacket.clearRateLimitEntry(p.getUUID());
        CombatTagTickHandler.markUntagged(p.getUUID());
        CombatEnforcementEvents.clearBlockedMessageEntry(p.getUUID());
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
        LOGGER.debug("Syncing state for {} (enabled={}, tagged={})", p.getScoreboardName(), d.isEnabled(), d.isTagged(now));

        if (d.isTagged(now)) {
            // Re-engage tag-expiration tick tracker so logout+login preserves the tag_expired notification
            CombatTagTickHandler.markTagged(p.getUUID());
            if (CTConfig.forceCombatWhileTagged.get() && !d.isEnabled()) {
                d.setEnabled(true);
                d.save(p);
                p.sendSystemMessage(Component.translatable("combattoggle.msg.forcing_combat", TextUtil.formatRemaining(d.getCombatTagUntilMs() - now)));
            }
        }

        // Update scoreboard team for nameplate color
        TeamManager.updatePlayerTeam(p, d.isEnabled());

        PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.isEnabled(), Math.max(0, d.getCombatTagUntilMs() - now), d.getRemainingCooldown(now)));
    }
}
