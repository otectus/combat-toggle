package com.runecraft.combattoggle.events;

import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.data.CombatToggleData;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.network.PacketHandler;
import com.runecraft.combattoggle.network.S2CSyncStatePacket;
import com.runecraft.combattoggle.util.TeamManager;
import com.runecraft.combattoggle.util.TextUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.runecraft.combattoggle.CombatToggle.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public final class CombatEnforcementEvents {

    private static final Map<UUID, Long> lastBlockedMessageTime = new HashMap<>();
    private static final long BLOCKED_MESSAGE_COOLDOWN_MS = 5000L;

    public static void clearBlockedMessageEntry(UUID playerId) {
        lastBlockedMessageTime.remove(playerId);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        // Resolve attacker: direct hit or projectile owner
        ServerPlayer attacker;
        var causingEntity = event.getSource().getEntity();
        if (causingEntity instanceof ServerPlayer sp) {
            attacker = sp;
        } else {
            // Fallback: resolve through the direct entity (the projectile itself)
            var directEntity = event.getSource().getDirectEntity();
            if (directEntity instanceof Projectile proj && proj.getOwner() instanceof ServerPlayer owner) {
                attacker = owner;
            } else {
                return;
            }
        }

        if (attacker == victim) return; // self-damage guard

        long now = System.currentTimeMillis();

        CombatToggleData a = CombatToggleData.get(attacker);
        CombatToggleData v = CombatToggleData.get(victim);

        boolean requireBoth = CTConfig.requireBothCombatEnabled.get();
        boolean allowed = requireBoth ? (a.isEnabled() && v.isEnabled()) : a.isEnabled();

        CombatToggle.LOGGER.debug("PvP event: {} -> {} (attacker={}, victim={}, allowed={})",
                attacker.getScoreboardName(), victim.getScoreboardName(), a.isEnabled(), v.isEnabled(), allowed);

        if (!allowed) {
            event.setCanceled(true);
            CombatToggle.LOGGER.debug("PvP blocked between {} and {}", attacker.getScoreboardName(), victim.getScoreboardName());

            // Throttled feedback to attacker
            long lastMsg = lastBlockedMessageTime.getOrDefault(attacker.getUUID(), 0L);
            if ((now - lastMsg) >= BLOCKED_MESSAGE_COOLDOWN_MS) {
                if (requireBoth && !v.isEnabled()) {
                    attacker.sendSystemMessage(Component.translatable("combattoggle.msg.pvp_blocked_target_peace", victim.getScoreboardName()));
                } else if (!a.isEnabled()) {
                    attacker.sendSystemMessage(Component.translatable("combattoggle.msg.pvp_blocked_self_peace"));
                }
                lastBlockedMessageTime.put(attacker.getUUID(), now);
            }
            return;
        }

        // Capture state before modifications for conditional sync
        boolean origAttackerEnabled = a.isEnabled();
        boolean origVictimEnabled = v.isEnabled();
        long origAttackerTag = a.getCombatTagUntilMs();
        long origVictimTag = v.getCombatTagUntilMs();
        long origAttackerPvp = a.getLastPvpMs();
        long origVictimPvp = v.getLastPvpMs();

        // PvP is allowed, apply combat tag to both (notify on first tag only)
        boolean attackerWasTagged = a.isTagged(now);
        boolean victimWasTagged = v.isTagged(now);

        a.applyCombatTag(now);
        v.applyCombatTag(now);

        int tagSeconds = CTConfig.combatTagSeconds.get();
        if (tagSeconds > 0) {
            if (!attackerWasTagged) {
                attacker.sendSystemMessage(Component.translatable("combattoggle.msg.combat_tagged", tagSeconds));
                CombatTagTickHandler.markTagged(attacker.getUUID());
            }
            if (!victimWasTagged) {
                victim.sendSystemMessage(Component.translatable("combattoggle.msg.combat_tagged", tagSeconds));
                CombatTagTickHandler.markTagged(victim.getUUID());
            }
        }

        // Track PvP activity for cooldown system
        if (CTConfig.cooldownTriggersOnPvp.get()) {
            a.setLastPvpMs(now);
            v.setLastPvpMs(now);
        }

        if (CTConfig.forceCombatWhileTagged.get()) {
            if (!a.isEnabled()) {
                a.setEnabled(true);
                TeamManager.updatePlayerTeam(attacker, true);
            }
            if (!v.isEnabled()) {
                v.setEnabled(true);
                TeamManager.updatePlayerTeam(victim, true);
            }
        }

        a.save(attacker);
        v.save(victim);

        // Sync only if state actually changed
        boolean attackerChanged = (a.isEnabled() != origAttackerEnabled)
                || (a.getCombatTagUntilMs() != origAttackerTag)
                || (a.getLastPvpMs() != origAttackerPvp);
        boolean victimChanged = (v.isEnabled() != origVictimEnabled)
                || (v.getCombatTagUntilMs() != origVictimTag)
                || (v.getLastPvpMs() != origVictimPvp);

        if (attackerChanged) {
            PacketHandler.sendToPlayer(attacker, new S2CSyncStatePacket(a.isEnabled(), Math.max(0, a.getCombatTagUntilMs() - now), a.getRemainingCooldown(now)));
        }
        if (victimChanged) {
            PacketHandler.sendToPlayer(victim, new S2CSyncStatePacket(v.isEnabled(), Math.max(0, v.getCombatTagUntilMs() - now), v.getRemainingCooldown(now)));
        }
    }
}
