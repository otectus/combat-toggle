package com.runecraft.combattoggle.network;

import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.api.events.CombatToggleStateChangeEvent;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.data.CombatToggleData;
import com.runecraft.combattoggle.data.ToggleDirection;
import com.runecraft.combattoggle.util.TeamManager;
import com.runecraft.combattoggle.util.TextUtil;
import net.minecraft.Util;
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

            // Rate limit toggle requests (in-memory only, monotonic ms is fine here)
            long reqTimeMs = Util.getMillis();
            UUID playerId = p.getUUID();
            Long lastReq = lastRequestTime.get(playerId);
            long nowTick = p.serverLevel().getGameTime();
            if (lastReq != null && (reqTimeMs - lastReq) < MIN_REQUEST_INTERVAL_MS) {
                CombatToggle.LOGGER.debug("Toggle rate-limited for {}", p.getScoreboardName());
                // Surface the drop: resync client state (keeps HUD in sync) and throttle a chat hint
                CombatToggleData state = CombatToggleData.get(p);
                PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(state.isEnabled(), state.getCombatTagRemainingMs(nowTick), state.getRemainingCooldownMs(nowTick)));
                Long lastMsg = lastRateLimitMsgTime.get(playerId);
                if (lastMsg == null || (reqTimeMs - lastMsg) >= RATE_LIMIT_MSG_THROTTLE_MS) {
                    p.sendSystemMessage(Component.translatable("combattoggle.msg.toggle_rate_limited"));
                    lastRateLimitMsgTime.put(playerId, reqTimeMs);
                }
                return;
            }
            lastRequestTime.put(playerId, reqTimeMs);

            CombatToggleData d = CombatToggleData.get(p);

            // Determine what mode player wants to switch to
            ToggleDirection direction = ToggleDirection.nextFor(d.isEnabled());
            if (CombatToggle.LOGGER.isDebugEnabled()) {
                CombatToggle.LOGGER.debug("Toggle request from {} (current={}, direction={})", p.getScoreboardName(), d.isEnabled(), direction);
            }

            // Combat tag restrictions
            if (d.isTagged(nowTick) && !CTConfig.allowToggleWhileTagged.get()) {
                if (direction == ToggleDirection.TO_PEACE) {
                    CombatToggle.LOGGER.debug("Toggle denied for {} -- combat tagged", p.getScoreboardName());
                    p.sendSystemMessage(Component.translatable("combattoggle.msg.cannot_toggle_tagged", TextUtil.formatRemaining(d.getCombatTagRemainingMs(nowTick))));
                    PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.isEnabled(), d.getCombatTagRemainingMs(nowTick), d.getRemainingCooldownMs(nowTick)));
                    return;
                }
            }

            // Check cooldown (PvP-triggered or toggle-triggered)
            if (d.isCooldownActiveForDirection(nowTick, direction)) {
                long remainingMs = d.getRemainingCooldownMs(nowTick);
                CombatToggleData.CooldownSource source = d.getDominantCooldownSource(nowTick);
                Component reason = source == CombatToggleData.CooldownSource.PVP
                        ? Component.translatable("combattoggle.msg.cooldown_reason_pvp")
                        : Component.translatable("combattoggle.msg.cooldown_reason_toggle");
                CombatToggle.LOGGER.debug("Toggle denied for {} -- cooldown active ({})", p.getScoreboardName(), source);
                p.sendSystemMessage(Component.translatable("combattoggle.msg.cooldown_active", reason, TextUtil.formatRemaining(remainingMs)));
                PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.isEnabled(), d.getCombatTagRemainingMs(nowTick), d.getRemainingCooldownMs(nowTick)));
                return;
            }

            boolean wantCombat = !d.isEnabled();
            CombatToggleStateChangeEvent stateEvent = new CombatToggleStateChangeEvent(p, wantCombat,
                    CombatToggleStateChangeEvent.Reason.PLAYER_TOGGLE);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(stateEvent)) {
                // Another mod cancelled the transition. Resync the client so the HUD doesn't lock in a stale state.
                PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.isEnabled(), d.getCombatTagRemainingMs(nowTick), d.getRemainingCooldownMs(nowTick)));
                return;
            }
            d.setEnabled(wantCombat);

            if (CTConfig.cooldownTriggersOnToggle.get()) {
                d.setLastToggleTick(nowTick);
            }

            // Update scoreboard team for nameplate color
            TeamManager.updatePlayerTeam(p, d.isEnabled());

            Component mode = d.isEnabled()
                    ? Component.translatable("combattoggle.msg.mode_combat")
                    : Component.translatable("combattoggle.msg.mode_peace");
            CombatToggle.LOGGER.info("Player {} toggled to {}", p.getScoreboardName(), d.isEnabled() ? "COMBAT" : "PEACE");
            p.sendSystemMessage(Component.translatable("combattoggle.msg.mode_set", mode));

            PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.isEnabled(), d.getCombatTagRemainingMs(nowTick), d.getRemainingCooldownMs(nowTick)));
        });
        c.setPacketHandled(true);
    }
}
