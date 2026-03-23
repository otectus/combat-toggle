package com.runecraft.combattoggle.events;

import com.runecraft.combattoggle.data.CombatToggleData;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.network.PacketHandler;
import com.runecraft.combattoggle.network.S2CSyncStatePacket;
import com.runecraft.combattoggle.util.TeamManager;
import com.runecraft.combattoggle.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.runecraft.combattoggle.CombatToggle.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public final class CombatEnforcementEvents {

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
        boolean allowed = requireBoth ? (a.enabled && v.enabled) : a.enabled;

        if (!allowed) {
            event.setCanceled(true);
            return;
        }

        // PvP is allowed, apply combat tag to both (notify on first tag only)
        boolean attackerWasTagged = a.isTagged(now);
        boolean victimWasTagged = v.isTagged(now);

        a.applyCombatTag(now);
        v.applyCombatTag(now);

        int tagSeconds = CTConfig.combatTagSeconds.get();
        if (tagSeconds > 0) {
            if (!attackerWasTagged)
                attacker.sendSystemMessage(TextUtil.system("[Combat Toggle] Combat-tagged for " + tagSeconds + "s"));
            if (!victimWasTagged)
                victim.sendSystemMessage(TextUtil.system("[Combat Toggle] Combat-tagged for " + tagSeconds + "s"));
        }

        // Track PvP activity for cooldown system
        if (CTConfig.cooldownTriggersOnPvp.get()) {
            a.lastPvpMs = now;
            v.lastPvpMs = now;
        }

        if (CTConfig.forceCombatWhileTagged.get()) {
            if (!a.enabled) {
                a.enabled = true;
                TeamManager.updatePlayerTeam(attacker, true);
            }
            if (!v.enabled) {
                v.enabled = true;
                TeamManager.updatePlayerTeam(victim, true);
            }
        }

        a.save(attacker);
        v.save(victim);

        // Sync if forced changes happened
        PacketHandler.sendToPlayer(attacker, new S2CSyncStatePacket(a.enabled, Math.max(0, a.combatTagUntilMs - now), a.getRemainingCooldown(now)));
        PacketHandler.sendToPlayer(victim, new S2CSyncStatePacket(v.enabled, Math.max(0, v.combatTagUntilMs - now), v.getRemainingCooldown(now)));
    }
}
