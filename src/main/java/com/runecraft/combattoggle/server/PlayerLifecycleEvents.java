package com.runecraft.combattoggle.server;

import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.common.CTConfig;
import com.runecraft.combattoggle.common.data.CombatToggleData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.runecraft.combattoggle.CombatToggle.MODID;

/**
 * Wires capability state into the player lifecycle: legacy migration on first login under 1.2.0,
 * team / sync re-application on login + respawn, and per-player in-memory cleanup on logout.
 *
 * <p>Clone-on-respawn is owned by {@link com.runecraft.combattoggle.common.data.CombatToggleCapability.ForgeBus}
 * — the capability copies its own state.
 */
@Mod.EventBusSubscriber(modid = MODID)
public final class PlayerLifecycleEvents {

    private static final String LEGACY_ROOT = "combat_toggle";

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        migrateLegacyData(p);
        syncAndEnforce(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        syncAndEnforce(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        TeamManager.removePlayerFromTeams(p);
        ToggleService.clearPlayerEntries(p.getUUID());
        CombatEnforcementEvents.clearBlockedMessageEntry(p.getUUID());
    }

    /**
     * One-shot migration from the pre-1.2.0 {@code combat_toggle} compound on the player's
     * persistent data into the new capability. Wall-clock-millis deadlines from 1.1.x cannot be
     * meaningfully converted to game ticks, so only the boolean {@code enabled} flag is preserved;
     * stale tag/cooldown timers are silently dropped. {@code tag_expiry_notified} is set to true
     * so we don't fire a phantom "tag expired" message after migration.
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
        d.setTagExpiryNotified(true);
        persistent.remove(LEGACY_ROOT);
        CombatToggle.LOGGER.info("Migrated legacy combat_toggle data for {} (preserved mode={}, dropped expired timers)",
                p.getScoreboardName(), d.isEnabled() ? "COMBAT" : "PEACE");
    }

    private static void syncAndEnforce(ServerPlayer p) {
        long nowTick = p.getLevel().getGameTime();
        CombatToggleData d = CombatToggleData.get(p);

        if (CombatToggle.LOGGER.isDebugEnabled()) {
            CombatToggle.LOGGER.debug("Syncing state for {} (enabled={}, tagged={})",
                    p.getScoreboardName(), d.isEnabled(), d.isTagged(nowTick));
        }

        if (d.isTagged(nowTick) && CTConfig.forceCombatWhileTagged.get() && !d.isEnabled()) {
            d.setEnabled(true);
            p.sendSystemMessage(Component.translatable("combattoggle.msg.forcing_combat",
                    TextHelper.formatRemaining(d.getCombatTagRemainingMs(nowTick))));
        } else if (!d.isTagged(nowTick) && d.getCombatTagUntilTick() > 0 && !d.isTagExpiryNotified()) {
            // Tag expired while the player was offline — fire the notification on first login back
            p.sendSystemMessage(Component.translatable("combattoggle.msg.tag_expired"));
            d.setTagExpiryNotified(true);
        }

        TeamManager.updatePlayerTeam(p, d.isEnabled());
        ToggleService.sendSync(p);
    }
}
