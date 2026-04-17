package com.runecraft.combattoggle.events;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.data.CombatToggleData;
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
        event.getDispatcher().register(
                Commands.literal("combattoggle")
                        // status: available to all players (permission level 0)
                        .then(Commands.literal("status")
                                .executes(CommandEvents::status))
                        // Admin commands: require OP level 2
                        .then(Commands.literal("get")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> get(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("set")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .executes(ctx -> set(ctx,
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "mode"),
                                                        false))
                                                .then(Commands.argument("bypassCooldown", BoolArgumentType.bool())
                                                        .executes(ctx -> set(ctx,
                                                                EntityArgument.getPlayer(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "mode"),
                                                                BoolArgumentType.getBool(ctx, "bypassCooldown")))))))
                        .then(Commands.literal("resetcooldown")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> resetCooldown(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("tag")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> tag(ctx, EntityArgument.getPlayer(ctx, "player"), CTConfig.combatTagSeconds.get()))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                                .executes(ctx -> tag(ctx,
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "seconds"))))))
                        .then(Commands.literal("untag")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> untag(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("reload")
                                .requires(s -> s.hasPermission(2))
                                .executes(CommandEvents::reload))
        );
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer p)) {
            source.sendFailure(Component.translatable("combattoggle.cmd.player_only"));
            return 0;
        }
        long now = System.currentTimeMillis();
        CombatToggleData d = CombatToggleData.get(p);

        Component mode = d.isEnabled()
                ? Component.translatable("combattoggle.msg.mode_combat")
                : Component.translatable("combattoggle.msg.mode_peace");

        p.sendSystemMessage(Component.translatable("combattoggle.msg.status_header"));
        p.sendSystemMessage(Component.translatable("combattoggle.msg.status_mode", mode));
        if (d.isTagged(now)) {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.status_tagged_yes", TextUtil.formatRemaining(d.getCombatTagUntilMs() - now)));
        } else {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.status_tagged_no"));
        }
        if (d.isCooldownActive(now, true)) {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.status_cooldown_yes", TextUtil.formatRemaining(d.getRemainingCooldown(now))));
        } else {
            p.sendSystemMessage(Component.translatable("combattoggle.msg.status_cooldown_no"));
        }
        return 1;
    }

    private static int get(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CombatToggleData d = CombatToggleData.get(target);
        long now = System.currentTimeMillis();
        LOGGER.info("Admin {} used /combattoggle get on {}", ctx.getSource().getTextName(), target.getScoreboardName());
        ctx.getSource().sendSuccess(() -> Component.translatable("combattoggle.cmd.get", d.isEnabled() ? "COMBAT" : "PEACE", d.isTagged(now)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String mode, boolean bypass) {
        boolean wantCombat;
        if (mode.equalsIgnoreCase("combat")) wantCombat = true;
        else if (mode.equalsIgnoreCase("peace")) wantCombat = false;
        else {
            ctx.getSource().sendFailure(Component.translatable("combattoggle.cmd.mode_invalid"));
            return 0;
        }

        CombatToggleData d = CombatToggleData.get(target);
        long now = System.currentTimeMillis();

        if (!bypass || !CTConfig.allowAdminBypassCooldown.get()) {
            if (d.isCooldownActive(now, !wantCombat)) {
                long remaining = d.getRemainingCooldown(now);
                ctx.getSource().sendFailure(Component.translatable("combattoggle.cmd.cooldown_remaining", TextUtil.formatRemaining(remaining)));
                return 0;
            }
        }

        d.setEnabled(wantCombat);
        if (CTConfig.cooldownTriggersOnToggle.get()) {
            d.setLastToggleMs(now);
        }
        d.save(target);

        // Update scoreboard team for nameplate color
        TeamManager.updatePlayerTeam(target, d.isEnabled());

        LOGGER.info("Admin {} used /combattoggle set on {} -> {}", ctx.getSource().getTextName(), target.getScoreboardName(), wantCombat ? "COMBAT" : "PEACE");
        ctx.getSource().sendSuccess(() -> Component.translatable("combattoggle.cmd.set_success", target.getScoreboardName(), wantCombat ? "COMBAT" : "PEACE"), true);
        target.sendSystemMessage(Component.translatable("combattoggle.msg.admin_set_mode", wantCombat ? "COMBAT" : "PEACE"));

        PacketHandler.sendToPlayer(target, new S2CSyncStatePacket(d.isEnabled(), Math.max(0, d.getCombatTagUntilMs() - now), d.getRemainingCooldown(now)));
        return 1;
    }

    private static int resetCooldown(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        LOGGER.info("Admin {} used /combattoggle resetcooldown on {}", ctx.getSource().getTextName(), target.getScoreboardName());
        CombatToggleData d = CombatToggleData.get(target);
        d.setLastToggleMs(0L);
        d.setLastPvpMs(0L);
        d.save(target);

        long now = System.currentTimeMillis();
        ctx.getSource().sendSuccess(() -> Component.translatable("combattoggle.cmd.cooldown_reset", target.getScoreboardName()), true);
        PacketHandler.sendToPlayer(target, new S2CSyncStatePacket(d.isEnabled(), Math.max(0, d.getCombatTagUntilMs() - now), d.getRemainingCooldown(now)));
        return 1;
    }

    private static int tag(CommandContext<CommandSourceStack> ctx, ServerPlayer target, int seconds) {
        if (seconds <= 0) {
            ctx.getSource().sendFailure(Component.translatable("combattoggle.cmd.tag_zero"));
            return 0;
        }

        LOGGER.info("Admin {} used /combattoggle tag on {} for {}s", ctx.getSource().getTextName(), target.getScoreboardName(), seconds);
        CombatToggleData d = CombatToggleData.get(target);
        long now = System.currentTimeMillis();
        long until = now + (seconds * 1000L);
        if (until > d.getCombatTagUntilMs()) d.setCombatTagUntilMs(until);

        if (CTConfig.forceCombatWhileTagged.get()) {
            d.setEnabled(true);
            // Update team if forcing combat mode
            TeamManager.updatePlayerTeam(target, true);
        }

        d.save(target);

        CombatTagTickHandler.markTagged(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable("combattoggle.cmd.tagged", target.getScoreboardName(), seconds), true);
        target.sendSystemMessage(Component.translatable("combattoggle.msg.you_tagged", seconds));

        PacketHandler.sendToPlayer(target, new S2CSyncStatePacket(d.isEnabled(), Math.max(0, d.getCombatTagUntilMs() - now), d.getRemainingCooldown(now)));
        return 1;
    }

    private static int untag(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        LOGGER.info("Admin {} used /combattoggle untag on {}", ctx.getSource().getTextName(), target.getScoreboardName());
        CombatToggleData d = CombatToggleData.get(target);
        d.setCombatTagUntilMs(0L);
        d.save(target);
        CombatTagTickHandler.markUntagged(target.getUUID());

        long now = System.currentTimeMillis();
        ctx.getSource().sendSuccess(() -> Component.translatable("combattoggle.cmd.untagged", target.getScoreboardName()), true);
        target.sendSystemMessage(Component.translatable("combattoggle.msg.tag_cleared"));

        PacketHandler.sendToPlayer(target, new S2CSyncStatePacket(d.isEnabled(), Math.max(0, d.getCombatTagUntilMs() - now), d.getRemainingCooldown(now)));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        LOGGER.info("Admin {} used /combattoggle reload", ctx.getSource().getTextName());
        var server = ctx.getSource().getServer();
        TeamManager.ensureTeamsExist(server.getScoreboard());
        long now = System.currentTimeMillis();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CombatToggleData d = CombatToggleData.get(p);
            TeamManager.updatePlayerTeam(p, d.isEnabled());
            PacketHandler.sendToPlayer(p, new S2CSyncStatePacket(d.isEnabled(), Math.max(0, d.getCombatTagUntilMs() - now), d.getRemainingCooldown(now)));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("combattoggle.cmd.reload"), false);
        return 1;
    }
}
