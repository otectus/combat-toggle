package com.runecraft.combattoggle.server;

import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.common.CTConfig;
import com.runecraft.combattoggle.common.data.CombatToggleData;
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

/**
 * PvP enforcement, two-layered:
 * <ul>
 *   <li><b>{@link LivingAttackEvent} (HIGH priority)</b> — primary cancel. Fires before knockback,
 *       hurt sound, and the {@code PLAYER_HURT_ENTITY} advancement criterion, so cancelling here
 *       fully stops the hurt pipeline. (Cancelling at LivingHurtEvent alone — 1.1.0's behavior —
 *       still let attackers chain free knockback shots on Peace targets.)</li>
 *   <li><b>{@link LivingHurtEvent}</b> — defence-in-depth fallback for damage that bypasses
 *       {@code LivingEntity.hurt} and calls {@code actuallyHurt} directly. Also clears residual
 *       {@code deltaMovement} + {@code hurtMarked} in case knockback slipped through.</li>
 * </ul>
 *
 * <p>Attacker resolution walks the standard ownership chain so projectiles, primed TNT, and tamed
 * pets are gated correctly.
 */
@Mod.EventBusSubscriber(modid = MODID)
public final class CombatEnforcementEvents {

    private static final Map<UUID, Long> lastBlockedMessageMs = new HashMap<>();
    private static final long BLOCKED_MESSAGE_COOLDOWN_MS = 5000L;

    public static void clearBlockedMessageEntry(UUID playerId) {
        lastBlockedMessageMs.remove(playerId);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        ServerPlayer attacker = resolveAttacker(event.getSource());
        if (attacker == null || attacker == victim) return;
        if (CombatToggleData.isPvpAllowed(attacker, victim)) return;

        event.setCanceled(true);
        sendBlockedFeedback(attacker, victim);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        ServerPlayer attacker = resolveAttacker(event.getSource());
        if (attacker == null || attacker == victim) return;

        long nowTick = victim.getLevel().getGameTime();
        CombatToggleData a = CombatToggleData.get(attacker);
        CombatToggleData v = CombatToggleData.get(victim);
        boolean allowed = CombatToggleData.isPvpAllowed(a, v);

        if (CombatToggle.LOGGER.isDebugEnabled()) {
            CombatToggle.LOGGER.debug("PvP event: {} -> {} (attacker={}, victim={}, allowed={})",
                    attacker.getScoreboardName(), victim.getScoreboardName(), a.isEnabled(), v.isEnabled(), allowed);
        }

        if (!allowed) {
            event.setCanceled(true);
            // Clear residual velocity from any knockback that slipped past LivingAttackEvent.
            victim.setDeltaMovement(0, victim.getDeltaMovement().y, 0);
            victim.hurtMarked = false;
            sendBlockedFeedback(attacker, victim);
            return;
        }

        // PvP is allowed — apply combat tag to both, notify on first tag only.
        boolean attackerWasTagged = a.isTagged(nowTick);
        boolean victimWasTagged = v.isTagged(nowTick);
        a.applyCombatTag(nowTick);
        v.applyCombatTag(nowTick);

        int tagSeconds = CTConfig.combatTagSeconds.get();
        if (tagSeconds > 0) {
            if (!attackerWasTagged) attacker.sendSystemMessage(Component.translatable("combattoggle.msg.combat_tagged", tagSeconds));
            if (!victimWasTagged) victim.sendSystemMessage(Component.translatable("combattoggle.msg.combat_tagged", tagSeconds));
        }

        if (CTConfig.cooldownTriggersOnPvp.get()) {
            a.setLastPvpTick(nowTick);
            v.setLastPvpTick(nowTick);
        }

        if (CTConfig.forceCombatWhileTagged.get()) {
            forceCombatIfNeeded(attacker, a, nowTick);
            forceCombatIfNeeded(victim, v, nowTick);
        }

        // Resync both players (best-effort; vanilla clients silently drop).
        ToggleService.sendSync(attacker);
        ToggleService.sendSync(victim);
    }

    private static void forceCombatIfNeeded(ServerPlayer p, CombatToggleData d, long nowTick) {
        if (d.isEnabled()) return;
        d.setEnabled(true);
        TeamManager.updatePlayerTeam(p, true);
        p.sendSystemMessage(Component.translatable("combattoggle.msg.admin_force_flipped",
                TextHelper.formatRemaining(d.getCombatTagRemainingMs(nowTick))));
    }

    /**
     * Resolves the player ultimately responsible for a damage source. Walks the standard PvP
     * bypass vectors: direct hits, projectiles, primed TNT, and any owned entity (vanilla tamables
     * and modded summons via {@link OwnableEntity}).
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

    private static void sendBlockedFeedback(ServerPlayer attacker, ServerPlayer victim) {
        long now = Util.getMillis();
        long lastMsg = lastBlockedMessageMs.getOrDefault(attacker.getUUID(), 0L);
        if ((now - lastMsg) < BLOCKED_MESSAGE_COOLDOWN_MS) return;

        boolean requireBoth = CTConfig.requireBothCombatEnabled.get();
        CombatToggleData a = CombatToggleData.get(attacker);
        CombatToggleData v = CombatToggleData.get(victim);
        if (requireBoth && !v.isEnabled()) {
            attacker.sendSystemMessage(Component.translatable("combattoggle.msg.pvp_blocked_target_peace", victim.getScoreboardName()));
        } else if (!a.isEnabled()) {
            attacker.sendSystemMessage(Component.translatable("combattoggle.msg.pvp_blocked_self_peace"));
        }
        lastBlockedMessageMs.put(attacker.getUUID(), now);
    }
}
