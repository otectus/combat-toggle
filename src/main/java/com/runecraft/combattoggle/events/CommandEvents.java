package com.runecraft.combattoggle.events;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.api.events.CombatTagAppliedEvent;
import com.runecraft.combattoggle.api.events.CombatToggleStateChangeEvent;
import com.runecraft.combattoggle.data.CombatToggleData;
import com.runecraft.combattoggle.data.ToggleDirection;
import com.runecraft.combattoggle.network.PacketHandler;
import com.runecraft.combattoggle.network.S2CSyncStatePacket;
import com.runecraft.combattoggle.util.TeamManager;
import com.runecraft.combattoggle.util.TextUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.runecraft.combattoggle.CombatToggle.LOGGER;
import static com.runecraft.combattoggle.CombatToggle.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public final class CommandEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("combattoggle")
                .then(Commands.literal("status").executes(CommandEvents::status))
                .then(Commands.literal("help").executes(CommandEvents::help))
                .then(Commands.literal("get")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> get(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(setModeBranch("combat", true))
                                .then(setModeBranch("peace", false))))
                .then(Commands.literal("resetcooldown")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> resetCooldown(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("tag")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> tag(ctx, EntityArgument.getPlayer(ctx, "player"), CTConfig.combatTagSeconds.get()))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> tag(ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "seconds"))))))
                .then(Commands.literal("untag")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> untag(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("resync")
                        .requires(s -> s.hasPermission(2))
                        .executes(CommandEvents::resync))
                .then(Commands.literal("reload")
                        .requires(s -> s.hasPermission(2))
                        .executes(CommandEvents::resync));

        event.getDispatcher().register(root);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> setModeBranch(String literal, boolean wantCombat) {
        return Commands.literal(literal)
                .executes(ctx -> set(ctx, EntityArgument.getPlayer(ctx, "player"), wantCombat, false))
                .then(Commands.argument("bypass", BoolArgumentType.bool())
                        .executes(ctx -> set(ctx, EntityArgument.getPlayer(ctx, "player"), wantCombat,
                                BoolArgumentType.getBool(ctx, "bypass"))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer p)) {
            source.sendFailure(Component.translatable("combattoggle.cmd.player_only"));
            return 0;
        }
        long nowTick = p.serverLevel().getGameTime();
        CombatToggleData d = CombatToggleData.get(p);

        Component mode = modeName(d.isEnabled());

        p.sendSystemMessage(Component.translatable("combattoggle.msg.status_header"));
        p.sendSystemMessage(Component.translatable("combattoggle.msg.status_mode", mode));
        if (d.isTagged(nowTick)) {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.status_tagged_yes", TextUtil.formatRemaining(d.getCombatTagRemainingMs(nowTick))));
        } else {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.status_tagged_no"));
        }
        // Check the cooldown in the direction of the *next* possible toggle, not always TO_PEACE.
        if (d.isCooldownActiveForDirection(nowTick, ToggleDirection.nextFor(d.isEnabled()))) {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.status_cooldown_yes", TextUtil.formatRemaining(d.getRemainingCooldownMs(nowTick))));
        } else {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.status_cooldown_no"));
        }
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        boolean isAdmin = src.hasPermission(2);
        src.sendSuccess(() -> Component.translatable("combattoggle.cmd.help_header"), false);
        src.sendSuccess(() -> Component.translatable("combattoggle.cmd.help_status"), false);
        src.sendSuccess(() -> Component.translatable("combattoggle.cmd.help_help"), false);
        if (isAdmin) {
            src.sendSuccess(() -> Component.translatable("combattoggle.cmd.help_get"), false);
            src.sendSuccess(() -> Component.translatable("combattoggle.cmd.help_set"), false);
            src.sendSuccess(() -> Component.translatable("combattoggle.cmd.help_resetcooldown"), false);
            src.sendSuccess(() -> Component.translatable("combattoggle.cmd.help_tag"), false);
            src.sendSuccess(() -> Component.translatable("combattoggle.cmd.help_untag"), false);
            src.sendSuccess(() -> Component.translatable("combattoggle.cmd.help_resync"), false);
        }
        return 1;
    }

    private static int get(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CombatToggleData d = CombatToggleData.get(target);
        long nowTick = target.serverLevel().getGameTime();
        LOGGER.info("Admin {} used /combattoggle get on {}", ctx.getSource().getTextName(), target.getScoreboardName());
        Component mode = modeName(d.isEnabled());
        ctx.getSource().sendSuccess(() -> Component.translatable("combattoggle.cmd.get", mode, d.isTagged(nowTick)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, ServerPlayer target, boolean wantCombat, boolean bypass) {
        CombatToggleData d = CombatToggleData.get(target);
        long nowTick = target.serverLevel().getGameTime();

        // Tag-guard: if force-combat-while-tagged is on and the player is currently tagged,
        // refuse a Peace transition unless the admin opted into bypass.
        if (!wantCombat && d.isTagged(nowTick) && CTConfig.forceCombatWhileTagged.get() && !bypass) {
            ctx.getSource().sendFailure(Component.translatable("combattoggle.cmd.set_blocked_tagged",
                    target.getScoreboardName(), TextUtil.formatRemaining(d.getCombatTagRemainingMs(nowTick))));
            return 0;
        }

        if (!bypass || !CTConfig.allowAdminBypassCooldown.get()) {
            if (d.isCooldownActiveForDirection(nowTick, ToggleDirection.toward(wantCombat))) {
                long remainingMs = d.getRemainingCooldownMs(nowTick);
                ctx.getSource().sendFailure(Component.translatable("combattoggle.cmd.cooldown_remaining", TextUtil.formatRemaining(remainingMs)));
                return 0;
            }
        }

        CombatToggleStateChangeEvent stateEvent = new CombatToggleStateChangeEvent(target, wantCombat,
                CombatToggleStateChangeEvent.Reason.ADMIN_SET);
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(stateEvent)) {
            ctx.getSource().sendFailure(Component.translatable("combattoggle.cmd.state_change_cancelled"));
            return 0;
        }

        d.setEnabled(wantCombat);
        if (CTConfig.cooldownTriggersOnToggle.get()) {
            d.setLastToggleTick(nowTick);
        }

        TeamManager.updatePlayerTeam(target, d.isEnabled());

        Component mode = modeName(wantCombat);
        LOGGER.info("Admin {} used /combattoggle set on {} -> {}", ctx.getSource().getTextName(), target.getScoreboardName(), wantCombat ? "COMBAT" : "PEACE");
        ctx.getSource().sendSuccess(() -> Component.translatable("combattoggle.cmd.set_success", target.getScoreboardName(), mode), true);
        target.sendSystemMessage(Component.translatable("combattoggle.msg.admin_set_mode", mode));

        PacketHandler.sendToPlayer(target, new S2CSyncStatePacket(d.isEnabled(), d.getCombatTagRemainingMs(nowTick), d.getRemainingCooldownMs(nowTick)));
        return 1;
    }

    private static int resetCooldown(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        LOGGER.info("Admin {} used /combattoggle resetcooldown on {}", ctx.getSource().getTextName(), target.getScoreboardName());
        CombatToggleData d = CombatToggleData.get(target);
        d.setLastToggleTick(0L);
        d.setLastPvpTick(0L);

        long nowTick = target.serverLevel().getGameTime();
        ctx.getSource().sendSuccess(() -> Component.translatable("combattoggle.cmd.cooldown_reset", target.getScoreboardName()), true);
        PacketHandler.sendToPlayer(target, new S2CSyncStatePacket(d.isEnabled(), d.getCombatTagRemainingMs(nowTick), d.getRemainingCooldownMs(nowTick)));
        return 1;
    }

    private static int tag(CommandContext<CommandSourceStack> ctx, ServerPlayer target, int seconds) {
        // Brigadier rejects 0 in the explicit-seconds form. Only the no-args form can pass 0
        // here, when combatTagSeconds is configured to 0 (tagging disabled).
        if (seconds <= 0) {
            ctx.getSource().sendFailure(Component.translatable("combattoggle.cmd.tag_disabled_in_config"));
            return 0;
        }

        LOGGER.info("Admin {} used /combattoggle tag on {} for {}s", ctx.getSource().getTextName(), target.getScoreboardName(), seconds);
        CombatToggleData d = CombatToggleData.get(target);
        long nowTick = target.serverLevel().getGameTime();
        long untilTick = nowTick + (seconds * CombatToggleData.TICKS_PER_SECOND);
        boolean tagAdvanced = untilTick > d.getCombatTagUntilTick();
        if (tagAdvanced) d.setCombatTagUntilTick(untilTick);

        if (CTConfig.forceCombatWhileTagged.get()) {
            d.setEnabled(true);
            TeamManager.updatePlayerTeam(target, true);
        }

        CombatTagTickHandler.markTagged(target.getUUID(), d.getCombatTagUntilTick());
        if (tagAdvanced) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new CombatTagAppliedEvent(target, seconds * CombatToggleData.TICKS_PER_SECOND, d.getCombatTagUntilTick()));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("combattoggle.cmd.tagged", target.getScoreboardName(), seconds), true);
        target.sendSystemMessage(Component.translatable("combattoggle.msg.you_tagged", seconds));

        PacketHandler.sendToPlayer(target, new S2CSyncStatePacket(d.isEnabled(), d.getCombatTagRemainingMs(nowTick), d.getRemainingCooldownMs(nowTick)));
        return 1;
    }

    private static int untag(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        LOGGER.info("Admin {} used /combattoggle untag on {}", ctx.getSource().getTextName(), target.getScoreboardName());
        CombatToggleData d = CombatToggleData.get(target);
        d.setCombatTagUntilTick(0L);
        d.setTagExpiryNotified(true); // admin-cleared tag should not also fire an "expired" message
        CombatTagTickHandler.markUntagged(target.getUUID());

        long nowTick = target.serverLevel().getGameTime();
        ctx.getSource().sendSuccess(() -> Component.translatable("combattoggle.cmd.untagged", target.getScoreboardName()), true);
        target.sendSystemMessage(Component.translatable("combattoggle.msg.tag_cleared"));

        PacketHandler.sendToPlayer(target, new S2CSyncStatePacket(d.isEnabled(), d.getCombatTagRemainingMs(nowTick), d.getRemainingCooldownMs(nowTick)));
        return 1;
    }

    private static int resync(CommandContext<CommandSourceStack> ctx) {
        LOGGER.info("Admin {} used /combattoggle resync", ctx.getSource().getTextName());
        var server = ctx.getSource().getServer();
        TeamManager.ensureTeamsExist(server.getScoreboard());
        long nowTick = server.overworld().getGameTime();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CombatToggleData d = CombatToggleData.get(p);
            TeamManager.updatePlayerTeam(p, d.isEnabled());
            PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.isEnabled(), d.getCombatTagRemainingMs(nowTick), d.getRemainingCooldownMs(nowTick)));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("combattoggle.cmd.resync"), false);
        return 1;
    }

    private static Component modeName(boolean combat) {
        return combat
                ? Component.translatable("combattoggle.msg.mode_combat")
                : Component.translatable("combattoggle.msg.mode_peace");
    }
}
