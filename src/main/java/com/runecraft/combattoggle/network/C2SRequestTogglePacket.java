package com.runecraft.combattoggle.network;

import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.data.CombatToggleData;
import com.runecraft.combattoggle.util.TeamManager;
import com.runecraft.combattoggle.util.TextUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class C2SRequestTogglePacket {

    private static final Map<UUID, Long> lastRequestTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastRateLimitMsgTime = new ConcurrentHashMap<>();
    private static final long MIN_REQUEST_INTERVAL_MS = 500L;
    private static final long RATE_LIMIT_MSG_THROTTLE_MS = 5000L;

    public static void clearRateLimitEntry(UUID playerId) {
        lastRequestTime.remove(playerId);
        lastRateLimitMsgTime.remove(playerId);
    }

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

            // Rate limit toggle requests
            long reqTime = System.currentTimeMillis();
            UUID playerId = p.getUUID();
            Long lastReq = lastRequestTime.get(playerId);
            if (lastReq != null && (reqTime - lastReq) < MIN_REQUEST_INTERVAL_MS) {
                CombatToggle.LOGGER.debug("Toggle rate-limited for {}", p.getScoreboardName());
                // Surface the drop: resync client state (keeps HUD in sync) and throttle a chat hint
                CombatToggleData state = CombatToggleData.get(p);
                PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(state.isEnabled(), Math.max(0, state.getCombatTagUntilMs() - reqTime), state.getRemainingCooldown(reqTime)));
                Long lastMsg = lastRateLimitMsgTime.get(playerId);
                if (lastMsg == null || (reqTime - lastMsg) >= RATE_LIMIT_MSG_THROTTLE_MS) {
                    p.sendSystemMessage(Component.translatable("combattoggle.msg.toggle_rate_limited"));
                    lastRateLimitMsgTime.put(playerId, reqTime);
                }
                return;
            }
            lastRequestTime.put(playerId, reqTime);

            long now = reqTime;
            CombatToggleData d = CombatToggleData.get(p);

            // Determine what mode player wants to switch to
            boolean wantPeace = d.isEnabled(); // If currently in Combat, they want Peace
            CombatToggle.LOGGER.debug("Toggle request from {} (current={}, wantPeace={})", p.getScoreboardName(), d.isEnabled(), wantPeace);

            // Combat tag restrictions
            if (d.isTagged(now) && !CTConfig.allowToggleWhileTagged.get()) {
                if (wantPeace) {
                    CombatToggle.LOGGER.debug("Toggle denied for {} -- combat tagged", p.getScoreboardName());
                    p.sendSystemMessage(Component.translatable("combattoggle.msg.cannot_toggle_tagged", TextUtil.formatRemaining(d.getCombatTagUntilMs() - now)));
                    PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.isEnabled(), Math.max(0, d.getCombatTagUntilMs() - now), d.getRemainingCooldown(now)));
                    return;
                }
            }

            // Check cooldown (PvP-triggered or toggle-triggered)
            if (d.isCooldownActive(now, wantPeace)) {
                long remaining = d.getRemainingCooldown(now);
                CombatToggleData.CooldownSource source = d.getDominantCooldownSource(now);
                Component reason = source == CombatToggleData.CooldownSource.PVP
                        ? Component.translatable("combattoggle.msg.cooldown_reason_pvp")
                        : Component.translatable("combattoggle.msg.cooldown_reason_toggle");
                CombatToggle.LOGGER.debug("Toggle denied for {} -- cooldown active ({})", p.getScoreboardName(), source);
                p.sendSystemMessage(Component.translatable("combattoggle.msg.cooldown_active", reason, TextUtil.formatRemaining(remaining)));
                PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.isEnabled(), Math.max(0, d.getCombatTagUntilMs() - now), d.getRemainingCooldown(now)));
                return;
            }

            d.setEnabled(!d.isEnabled());
            
            // Update lastToggleMs only if toggle-based cooldown is enabled
            if (CTConfig.cooldownTriggersOnToggle.get()) {
                d.setLastToggleMs(now);
            }
            
            d.save(p);

            // Update scoreboard team for nameplate color
            TeamManager.updatePlayerTeam(p, d.isEnabled());

            Component mode = d.isEnabled()
                    ? Component.translatable("combattoggle.msg.mode_combat")
                    : Component.translatable("combattoggle.msg.mode_peace");
            CombatToggle.LOGGER.info("Player {} toggled to {}", p.getScoreboardName(), d.isEnabled() ? "COMBAT" : "PEACE");
            p.sendSystemMessage(Component.translatable("combattoggle.msg.mode_set", mode));

            PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.isEnabled(), Math.max(0, d.getCombatTagUntilMs() - now), d.getRemainingCooldown(now)));
        });
        c.setPacketHandled(true);
    }
}
