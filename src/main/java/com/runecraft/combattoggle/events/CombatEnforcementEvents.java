package com.runecraft.combattoggle.events;

import com.runecraft.combattoggle.data.CombatToggleData;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.network.PacketHandler;
import com.runecraft.combattoggle.network.S2CSyncStatePacket;
import com.runecraft.combattoggle.util.TeamManager;
import com.runecraft.combattoggle.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.runecraft.combattoggle.CombatToggle.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public final class CombatEnforcementEvents {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;

        long now = System.currentTimeMillis();

        CombatToggleData a = CombatToggleData.get(attacker);
        CombatToggleData v = CombatToggleData.get(victim);

        boolean requireBoth = CTConfig.requireBothCombatEnabled.get();
        boolean allowed = requireBoth ? (a.enabled && v.enabled) : a.enabled;

        if (!allowed) {
            event.setCanceled(true);
            return;
        }

        // PvP is allowed, apply combat tag to both
        a.applyCombatTag(now);
        v.applyCombatTag(now);

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
        PacketHandler.sendToPlayer(attacker, new S2CSyncStatePacket(a.enabled, a.lastToggleMs, a.combatTagUntilMs));
        PacketHandler.sendToPlayer(victim, new S2CSyncStatePacket(v.enabled, v.lastToggleMs, v.combatTagUntilMs));
    }
}
