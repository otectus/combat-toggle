package com.runecraft.combattoggle.network;

import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.data.CombatToggleData;
import com.runecraft.combattoggle.util.TeamManager;
import com.runecraft.combattoggle.util.TextUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class C2SRequestTogglePacket {

    public static void encode(C2SRequestTogglePacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static C2SRequestTogglePacket decode(FriendlyByteBuf buf) {
        return new C2SRequestTogglePacket();
    }

    public static void handle(C2SRequestTogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer p = c.getSender();
            if (p == null) return;

            long now = System.currentTimeMillis();
            CombatToggleData d = CombatToggleData.get(p);

            // Determine what mode player wants to switch to
            boolean wantPeace = d.enabled; // If currently in Combat, they want Peace

            // Combat tag restrictions
            if (d.isTagged(now) && !CTConfig.allowToggleWhileTagged.get()) {
                // deny toggling to Peace while tagged
                if (wantPeace) {
                    p.sendSystemMessage(TextUtil.system("[Combat Toggle] Cannot toggle to Peace while combat-tagged. Remaining: " + TextUtil.formatRemaining(d.combatTagUntilMs - now)));
                    PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.enabled, Math.max(0, d.combatTagUntilMs - now), d.getRemainingCooldown(now)));
                    return;
                }
            }

            // Check cooldown (PvP-triggered or toggle-triggered)
            if (d.isCooldownActive(now, wantPeace)) {
                long remaining = d.getRemainingCooldown(now);
                String reason = CTConfig.cooldownTriggersOnPvp.get() ? "recent PvP activity" : "recent toggle";
                p.sendSystemMessage(TextUtil.system("[Combat Toggle] Cooldown active due to " + reason + ". Remaining: " + TextUtil.formatRemaining(remaining)));
                PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.enabled, Math.max(0, d.combatTagUntilMs - now), d.getRemainingCooldown(now)));
                return;
            }

            d.enabled = !d.enabled;
            
            // Update lastToggleMs only if toggle-based cooldown is enabled
            if (CTConfig.cooldownTriggersOnToggle.get()) {
                d.lastToggleMs = now;
            }
            
            d.save(p);

            // Update scoreboard team for nameplate color
            TeamManager.updatePlayerTeam(p, d.enabled);

            String mode = d.enabled ? "COMBAT" : "PEACE";
            p.sendSystemMessage(TextUtil.system("[Combat Toggle] Mode set to: " + mode));

            PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.enabled, Math.max(0, d.combatTagUntilMs - now), d.getRemainingCooldown(now)));
        });
        c.setPacketHandled(true);
    }
}
