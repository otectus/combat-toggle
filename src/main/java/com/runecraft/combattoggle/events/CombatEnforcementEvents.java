package com.runecraft.combattoggle.events;

import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.api.events.CombatTagAppliedEvent;
import com.runecraft.combattoggle.api.events.CombatToggleStateChangeEvent;
import com.runecraft.combattoggle.data.CombatToggleData;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.network.PacketHandler;
import com.runecraft.combattoggle.network.S2CSyncStatePacket;
import com.runecraft.combattoggle.util.TeamManager;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
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

    /**
     * Primary enforcement: cancels damage before knockback, hurt sound, and PLAYER_HURT_ENTITY criteria fire.
     * Fires at HIGH priority so we beat most other gameplay mods to the cancel.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        ServerPlayer attacker = resolveAttacker(event.getSource());
        if (attacker == null || attacker == victim) return;

        // Damage-type denylist (REVIEW 3.1): block specific vectors regardless of mode.
        String blockedTypeId = blockedDamageTypeId(event.getSource());
        if (blockedTypeId != null) {
            event.setCanceled(true);
            sendDamageTypeBlockedFeedback(attacker, blockedTypeId);
            return;
        }

        if (CombatToggleData.isPvpAllowed(attacker, victim)) return;

        event.setCanceled(true);
        sendBlockedFeedback(attacker, victim);
    }

    /**
     * Defence-in-depth: catches damage that bypasses {@code LivingEntity.hurt} and calls {@code actuallyHurt} directly.
     * Also clears any residual knockback velocity in case some mod applied it before LivingAttackEvent could cancel.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        ServerPlayer attacker = resolveAttacker(event.getSource());
        if (attacker == null || attacker == victim) return;

        // Defence-in-depth for the damage-type denylist too.
        String blockedTypeId = blockedDamageTypeId(event.getSource());
        if (blockedTypeId != null) {
            event.setCanceled(true);
            victim.setDeltaMovement(0, victim.getDeltaMovement().y, 0);
            victim.hurtMarked = false;
            sendDamageTypeBlockedFeedback(attacker, blockedTypeId);
            return;
        }

        long nowTick = victim.serverLevel().getGameTime();

        CombatToggleData a = CombatToggleData.get(attacker);
        CombatToggleData v = CombatToggleData.get(victim);

        boolean allowed = CombatToggleData.isPvpAllowed(a, v);

        if (CombatToggle.LOGGER.isDebugEnabled()) {
            CombatToggle.LOGGER.debug("PvP event: {} -> {} (attacker={}, victim={}, allowed={})",
                    attacker.getScoreboardName(), victim.getScoreboardName(), a.isEnabled(), v.isEnabled(), allowed);
        }

        if (!allowed) {
            event.setCanceled(true);
            // Clear residual velocity from any knockback that slipped past LivingAttackEvent (e.g. mod-applied early KB).
            victim.setDeltaMovement(0, victim.getDeltaMovement().y, 0);
            victim.hurtMarked = false;
            sendBlockedFeedback(attacker, victim);
            return;
        }

        // Capture state before modifications for conditional sync
        boolean origAttackerEnabled = a.isEnabled();
        boolean origVictimEnabled = v.isEnabled();
        long origAttackerTag = a.getCombatTagUntilTick();
        long origVictimTag = v.getCombatTagUntilTick();
        long origAttackerPvp = a.getLastPvpTick();
        long origVictimPvp = v.getLastPvpTick();

        // PvP is allowed, apply combat tag to both (notify on first tag only)
        boolean attackerWasTagged = a.isTagged(nowTick);
        boolean victimWasTagged = v.isTagged(nowTick);

        long origAttackerDeadline = a.getCombatTagUntilTick();
        long origVictimDeadline = v.getCombatTagUntilTick();
        a.applyCombatTag(nowTick);
        v.applyCombatTag(nowTick);

        int tagSeconds = CTConfig.combatTagSeconds.get();
        if (tagSeconds > 0) {
            long durationTicks = tagSeconds * CombatToggleData.TICKS_PER_SECOND;
            if (!attackerWasTagged) {
                attacker.sendSystemMessage(Component.translatable("combattoggle.msg.combat_tagged", tagSeconds));
                CombatTagTickHandler.markTagged(attacker.getUUID(), a.getCombatTagUntilTick());
            }
            if (!victimWasTagged) {
                victim.sendSystemMessage(Component.translatable("combattoggle.msg.combat_tagged", tagSeconds));
                CombatTagTickHandler.markTagged(victim.getUUID(), v.getCombatTagUntilTick());
            }
            if (a.getCombatTagUntilTick() > origAttackerDeadline) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        new CombatTagAppliedEvent(attacker, durationTicks, a.getCombatTagUntilTick()));
            }
            if (v.getCombatTagUntilTick() > origVictimDeadline) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        new CombatTagAppliedEvent(victim, durationTicks, v.getCombatTagUntilTick()));
            }
        }

        // Track PvP activity for cooldown system
        if (CTConfig.cooldownTriggersOnPvp.get()) {
            a.setLastPvpTick(nowTick);
            v.setLastPvpTick(nowTick);
        }

        if (CTConfig.forceCombatWhileTagged.get()) {
            if (!a.isEnabled()) {
                CombatToggleStateChangeEvent ev = new CombatToggleStateChangeEvent(attacker, true,
                        CombatToggleStateChangeEvent.Reason.FORCE_COMBAT_WHILE_TAGGED);
                if (!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(ev)) {
                    a.setEnabled(true);
                    TeamManager.updatePlayerTeam(attacker, true);
                    attacker.sendSystemMessage(Component.translatable("combattoggle.msg.admin_force_flipped",
                            com.runecraft.combattoggle.util.TextUtil.formatRemaining(a.getCombatTagRemainingMs(nowTick))));
                }
            }
            if (!v.isEnabled()) {
                CombatToggleStateChangeEvent ev = new CombatToggleStateChangeEvent(victim, true,
                        CombatToggleStateChangeEvent.Reason.FORCE_COMBAT_WHILE_TAGGED);
                if (!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(ev)) {
                    v.setEnabled(true);
                    TeamManager.updatePlayerTeam(victim, true);
                    victim.sendSystemMessage(Component.translatable("combattoggle.msg.admin_force_flipped",
                            com.runecraft.combattoggle.util.TextUtil.formatRemaining(v.getCombatTagRemainingMs(nowTick))));
                }
            }
        }

        // Sync only if state actually changed
        boolean attackerChanged = (a.isEnabled() != origAttackerEnabled)
                || (a.getCombatTagUntilTick() != origAttackerTag)
                || (a.getLastPvpTick() != origAttackerPvp);
        boolean victimChanged = (v.isEnabled() != origVictimEnabled)
                || (v.getCombatTagUntilTick() != origVictimTag)
                || (v.getLastPvpTick() != origVictimPvp);

        if (attackerChanged) {
            PacketHandler.sendToPlayer(attacker, new S2CSyncStatePacket(a.isEnabled(), a.getCombatTagRemainingMs(nowTick), a.getRemainingCooldownMs(nowTick)));
        }
        if (victimChanged) {
            PacketHandler.sendToPlayer(victim, new S2CSyncStatePacket(v.isEnabled(), v.getCombatTagRemainingMs(nowTick), v.getRemainingCooldownMs(nowTick)));
        }
    }

    /**
     * Resolves the player ultimately responsible for a damage source.
     * Walks the standard PvP bypass vectors: direct hits, projectiles, primed TNT, and any owned entity
     * (vanilla tamables and modded summons via the {@link OwnableEntity} interface).
     */
    private static ServerPlayer resolveAttacker(DamageSource src) {
        Entity e = src.getEntity();
        if (e instanceof ServerPlayer sp) return sp;
        Entity direct = src.getDirectEntity();
        if (direct instanceof Projectile p && p.getOwner() instanceof ServerPlayer sp) return sp;
        if (direct instanceof PrimedTnt tnt && tnt.getOwner() instanceof ServerPlayer sp) return sp;
        if (e instanceof OwnableEntity owned && owned.getOwner() instanceof ServerPlayer sp) return sp;
        if (direct instanceof OwnableEntity owned && owned.getOwner() instanceof ServerPlayer sp) return sp;
        return null;
    }

    /**
     * Returns the resource-location string of the damage type if it appears on the configured denylist,
     * or {@code null} if the damage is not denied by type. Cheap on the common (empty-list) path.
     */
    private static String blockedDamageTypeId(DamageSource src) {
        java.util.List<? extends String> blocked = CTConfig.blockedDamageTypes.get();
        if (blocked.isEmpty()) return null;
        return src.typeHolder().unwrapKey()
                .map(k -> k.location().toString())
                .filter(blocked::contains)
                .orElse(null);
    }

    /** Throttled chat hint to the attacker explaining a damage-type-based block. */
    private static void sendDamageTypeBlockedFeedback(ServerPlayer attacker, String damageTypeId) {
        long now = Util.getMillis();
        long lastMsg = lastBlockedMessageTime.getOrDefault(attacker.getUUID(), 0L);
        if ((now - lastMsg) < BLOCKED_MESSAGE_COOLDOWN_MS) return;
        attacker.sendSystemMessage(Component.translatable("combattoggle.msg.pvp_blocked_damage_type", damageTypeId));
        lastBlockedMessageTime.put(attacker.getUUID(), now);
    }

    /** Throttled chat hint to the attacker explaining why the hit was blocked. */
    private static void sendBlockedFeedback(ServerPlayer attacker, ServerPlayer victim) {
        long now = Util.getMillis();
        long lastMsg = lastBlockedMessageTime.getOrDefault(attacker.getUUID(), 0L);
        if ((now - lastMsg) < BLOCKED_MESSAGE_COOLDOWN_MS) return;
        boolean requireBoth = CTConfig.requireBothCombatEnabled.get();
        CombatToggleData a = CombatToggleData.get(attacker);
        CombatToggleData v = CombatToggleData.get(victim);
        if (requireBoth && !v.isEnabled()) {
            attacker.sendSystemMessage(Component.translatable("combattoggle.msg.pvp_blocked_target_peace", victim.getScoreboardName()));
        } else if (!a.isEnabled()) {
            attacker.sendSystemMessage(Component.translatable("combattoggle.msg.pvp_blocked_self_peace"));
        }
        lastBlockedMessageTime.put(attacker.getUUID(), now);
    }
}
