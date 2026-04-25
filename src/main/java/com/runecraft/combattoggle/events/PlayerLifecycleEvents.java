package com.runecraft.combattoggle.events;

import com.runecraft.combattoggle.data.CombatToggleData;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.network.C2SRequestTogglePacket;
import com.runecraft.combattoggle.network.PacketHandler;
import com.runecraft.combattoggle.network.S2CSyncStatePacket;
import com.runecraft.combattoggle.util.TeamManager;
import com.runecraft.combattoggle.api.events.CombatTagExpiredEvent;
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

    private static final String LEGACY_ROOT = "combat_toggle";

    // PlayerEvent.Clone is handled by CombatToggleCapability.ForgeBus.onClone — capability copies its own state.

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;

        // Fire the combat-log-aware event before clearing tracker state so listeners see the real tag remainder.
        long nowTick = p.serverLevel().getGameTime();
        CombatToggleData d = CombatToggleData.get(p);
        long tagRemainingTicks = Math.max(0L, d.getCombatTagUntilTick() - nowTick);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.runecraft.combattoggle.api.events.CombatLoggedOutEvent(p, tagRemainingTicks));

        TeamManager.removePlayerFromTeams(p);
        C2SRequestTogglePacket.clearRateLimitEntry(p.getUUID());
        CombatTagTickHandler.markUntagged(p.getUUID());
        CombatEnforcementEvents.clearBlockedMessageEntry(p.getUUID());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        migrateLegacyData(p);
        syncAndEnforce(p);
    }

    /**
     * One-shot migration from the pre-1.2.0 {@code combat_toggle} compound on the player's persistent data
     * into the new capability. Wall-clock-millis deadlines are dropped (see
     * {@link CombatToggleData#deserializeNBT(CompoundTag)}'s legacy threshold) — only the boolean
     * {@code enabled} flag is timeless and worth preserving.
     */
    private static void migrateLegacyData(ServerPlayer p) {
        CompoundTag persistent = p.getPersistentData();
        if (!persistent.contains(LEGACY_ROOT)) return;
        CompoundTag legacy = persistent.getCompound(LEGACY_ROOT);
        if (legacy.isEmpty()) {
            persistent.remove(LEGACY_ROOT);
            return;
        }
        CombatToggleData d = CombatToggleData.get(p);
        if (legacy.contains("enabled")) {
            d.setEnabled(legacy.getBoolean("enabled"));
        }
        // All timestamps are wall-clock from the old code path and meaningless against Util.getMillis(); drop.
        persistent.remove(LEGACY_ROOT);
        LOGGER.info("Migrated legacy combat_toggle data for {} (preserved mode={}, dropped expired timers)",
                p.getScoreboardName(), d.isEnabled() ? "COMBAT" : "PEACE");
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        syncAndEnforce(p);
    }

    private static void syncAndEnforce(ServerPlayer p) {
        long nowTick = p.serverLevel().getGameTime();
        CombatToggleData d = CombatToggleData.get(p);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Syncing state for {} (enabled={}, tagged={})", p.getScoreboardName(), d.isEnabled(), d.isTagged(nowTick));
        }

        if (d.isTagged(nowTick)) {
            // Re-engage tag-expiration tick tracker so logout+login preserves the tag_expired notification
            CombatTagTickHandler.markTagged(p.getUUID(), d.getCombatTagUntilTick());
            if (CTConfig.forceCombatWhileTagged.get() && !d.isEnabled()) {
                d.setEnabled(true);
                p.sendSystemMessage(Component.translatable("combattoggle.msg.forcing_combat", TextUtil.formatRemaining(d.getCombatTagRemainingMs(nowTick))));
            }
        } else if (d.getCombatTagUntilTick() > 0 && !d.isTagExpiryNotified()) {
            // Tag expired while the player was offline — fire the notification on first login back so external
            // tooling that watches chat for the expiry signal stays in sync. (REVIEW 3.11)
            p.sendSystemMessage(Component.translatable("combattoggle.msg.tag_expired"));
            d.setTagExpiryNotified(true);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new CombatTagExpiredEvent(p));
        }

        // Update scoreboard team for nameplate color
        TeamManager.updatePlayerTeam(p, d.isEnabled());

        PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.isEnabled(), d.getCombatTagRemainingMs(nowTick), d.getRemainingCooldownMs(nowTick)));
    }
}
