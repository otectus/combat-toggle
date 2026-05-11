package com.runecraft.combattoggle.server;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.common.CTConfig;
import com.runecraft.combattoggle.common.data.CombatToggleData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.runecraft.combattoggle.CombatToggle.MODID;

/**
 * Registers the player-facing toggle commands ({@code /ct}, {@code /combat}, {@code /peace}) plus
 * the {@code /combattoggle} namespace for status / help / admin operations.
 *
 * <p>{@code /ct} is the canonical baseline for vanilla clients (no mod installed client-side).
 */
@Mod.EventBusSubscriber(modid = MODID)
public final class CommandRegistry {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ct").executes(CommandRegistry::cmdToggleSelf));
        event.getDispatcher().register(
                Commands.literal("combat").executes(ctx -> cmdSetSelfMode(ctx, true)));
        event.getDispatcher().register(
                Commands.literal("peace").executes(ctx -> cmdSetSelfMode(ctx, false)));

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("combattoggle")
                .then(Commands.literal("toggle").executes(CommandRegistry::cmdToggleSelf))
                .then(Commands.literal("status").executes(CommandRegistry::cmdStatus))
                .then(Commands.literal("help").executes(CommandRegistry::cmdHelp))
                .then(Commands.literal("get")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> cmdGet(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(setModeBranch("combat", true))
                                .then(setModeBranch("peace", false))))
                .then(Commands.literal("resetcooldown")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> cmdResetCooldown(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("tag")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> cmdTag(ctx, EntityArgument.getPlayer(ctx, "player"), CTConfig.combatTagSeconds.get()))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> cmdTag(ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "seconds"))))))
                .then(Commands.literal("untag")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> cmdUntag(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("resync")
                        .requires(s -> s.hasPermission(2))
                        .executes(CommandRegistry::cmdResync))
                .then(Commands.literal("reload")
                        .requires(s -> s.hasPermission(2))
                        .executes(CommandRegistry::cmdResync));

        event.getDispatcher().register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> setModeBranch(String literal, boolean wantCombat) {
        return Commands.literal(literal)
                .executes(ctx -> cmdAdminSet(ctx, EntityArgument.getPlayer(ctx, "player"), wantCombat, false))
                .then(Commands.argument("bypass", BoolArgumentType.bool())
                        .executes(ctx -> cmdAdminSet(ctx, EntityArgument.getPlayer(ctx, "player"), wantCombat,
                                BoolArgumentType.getBool(ctx, "bypass"))));
    }

    // ----- player commands

    private static int cmdToggleSelf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = playerOrFail(ctx);
        if (p == null) return 0;
        ToggleService.toggle(p);
        return 1;
    }

    private static int cmdSetSelfMode(CommandContext<CommandSourceStack> ctx, boolean wantCombat) {
        ServerPlayer p = playerOrFail(ctx);
        if (p == null) return 0;
        ToggleService.setMode(p, wantCombat);
        return 1;
    }

    private static int cmdStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = playerOrFail(ctx);
        if (p == null) return 0;
        long nowTick = p.getLevel().getGameTime();
        CombatToggleData d = CombatToggleData.get(p);

        p.sendSystemMessage(Component.translatable("combattoggle.msg.status_header"));
        p.sendSystemMessage(Component.translatable("combattoggle.msg.status_mode", TextHelper.modeName(d.isEnabled())));
        if (d.isTagged(nowTick)) {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.status_tagged_yes",
                    TextHelper.formatRemaining(d.getCombatTagRemainingMs(nowTick))));
        } else {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.status_tagged_no"));
        }
        // Ask the cooldown question in the direction of the player's *next* possible toggle.
        boolean wantCombatNext = !d.isEnabled();
        if (d.isCooldownActiveForDirection(nowTick, wantCombatNext)) {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.status_cooldown_yes",
                    TextHelper.formatRemaining(d.getRemainingCooldownMs(nowTick))));
        } else {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.status_cooldown_no"));
        }
        return 1;
    }

    private static int cmdHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        boolean isAdmin = src.hasPermission(2);
        src.sendSuccess(Component.translatable("combattoggle.cmd.help_header"), false);
        src.sendSuccess(Component.translatable("combattoggle.cmd.help_ct"), false);
        src.sendSuccess(Component.translatable("combattoggle.cmd.help_combat_peace"), false);
        src.sendSuccess(Component.translatable("combattoggle.cmd.help_status"), false);
        src.sendSuccess(Component.translatable("combattoggle.cmd.help_help"), false);
        if (isAdmin) {
            src.sendSuccess(Component.translatable("combattoggle.cmd.help_get"), false);
            src.sendSuccess(Component.translatable("combattoggle.cmd.help_set"), false);
            src.sendSuccess(Component.translatable("combattoggle.cmd.help_resetcooldown"), false);
            src.sendSuccess(Component.translatable("combattoggle.cmd.help_tag"), false);
            src.sendSuccess(Component.translatable("combattoggle.cmd.help_untag"), false);
            src.sendSuccess(Component.translatable("combattoggle.cmd.help_resync"), false);
        }
        return 1;
    }

    // ----- admin commands

    private static int cmdGet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        long nowTick = target.getLevel().getGameTime();
        CombatToggleData d = CombatToggleData.get(target);
        CombatToggle.LOGGER.info("Admin {} used /combattoggle get on {}", ctx.getSource().getTextName(), target.getScoreboardName());
        ctx.getSource().sendSuccess(Component.translatable("combattoggle.cmd.get",
                TextHelper.modeName(d.isEnabled()), d.isTagged(nowTick)), false);
        return 1;
    }

    private static int cmdAdminSet(CommandContext<CommandSourceStack> ctx, ServerPlayer target, boolean wantCombat, boolean bypass) {
        long nowTick = target.getLevel().getGameTime();
        CombatToggleData d = CombatToggleData.get(target);

        // Pre-flight checks for failure messaging (the service signals via boolean, but we want to
        // produce specific error messages here based on which guard tripped).
        if (!wantCombat && d.isTagged(nowTick) && CTConfig.forceCombatWhileTagged.get() && !bypass) {
            ctx.getSource().sendFailure(Component.translatable("combattoggle.cmd.set_blocked_tagged",
                    target.getScoreboardName(), TextHelper.formatRemaining(d.getCombatTagRemainingMs(nowTick))));
            return 0;
        }
        if (!bypass || !CTConfig.allowAdminBypassCooldown.get()) {
            if (d.isCooldownActiveForDirection(nowTick, wantCombat)) {
                ctx.getSource().sendFailure(Component.translatable("combattoggle.cmd.cooldown_remaining",
                        TextHelper.formatRemaining(d.getRemainingCooldownMs(nowTick))));
                return 0;
            }
        }

        if (!ToggleService.adminSet(target, wantCombat, bypass)) {
            // Service refused — should be caught by the pre-flight, but defend in depth.
            ctx.getSource().sendFailure(Component.translatable("combattoggle.cmd.cooldown_remaining",
                    TextHelper.formatRemaining(d.getRemainingCooldownMs(nowTick))));
            return 0;
        }

        Component mode = TextHelper.modeName(wantCombat);
        CombatToggle.LOGGER.info("Admin {} used /combattoggle set on {} -> {}",
                ctx.getSource().getTextName(), target.getScoreboardName(), wantCombat ? "COMBAT" : "PEACE");
        ctx.getSource().sendSuccess(Component.translatable("combattoggle.cmd.set_success", target.getScoreboardName(), mode), true);
        return 1;
    }

    private static int cmdResetCooldown(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CombatToggle.LOGGER.info("Admin {} used /combattoggle resetcooldown on {}",
                ctx.getSource().getTextName(), target.getScoreboardName());
        ToggleService.resetCooldown(target);
        ctx.getSource().sendSuccess(Component.translatable("combattoggle.cmd.cooldown_reset", target.getScoreboardName()), true);
        return 1;
    }

    private static int cmdTag(CommandContext<CommandSourceStack> ctx, ServerPlayer target, int seconds) {
        if (seconds <= 0) {
            ctx.getSource().sendFailure(Component.translatable("combattoggle.cmd.tag_disabled_in_config"));
            return 0;
        }
        CombatToggle.LOGGER.info("Admin {} used /combattoggle tag on {} for {}s",
                ctx.getSource().getTextName(), target.getScoreboardName(), seconds);
        ToggleService.applyTag(target, seconds);
        ctx.getSource().sendSuccess(Component.translatable("combattoggle.cmd.tagged",
                target.getScoreboardName(), seconds), true);
        return 1;
    }

    private static int cmdUntag(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CombatToggle.LOGGER.info("Admin {} used /combattoggle untag on {}",
                ctx.getSource().getTextName(), target.getScoreboardName());
        ToggleService.clearTag(target);
        ctx.getSource().sendSuccess(Component.translatable("combattoggle.cmd.untagged", target.getScoreboardName()), true);
        return 1;
    }

    private static int cmdResync(CommandContext<CommandSourceStack> ctx) {
        CombatToggle.LOGGER.info("Admin {} used /combattoggle resync", ctx.getSource().getTextName());
        var server = ctx.getSource().getServer();
        TeamManager.ensureTeamsExist(server.getScoreboard());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CombatToggleData d = CombatToggleData.get(p);
            TeamManager.updatePlayerTeam(p, d.isEnabled());
            ToggleService.sendSync(p);
        }
        ctx.getSource().sendSuccess(Component.translatable("combattoggle.cmd.resync"), false);
        return 1;
    }

    private static ServerPlayer playerOrFail(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer p) return p;
        ctx.getSource().sendFailure(Component.translatable("combattoggle.cmd.player_only"));
        return null;
    }

    private CommandRegistry() {}
}
