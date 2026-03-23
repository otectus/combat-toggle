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
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.runecraft.combattoggle.CombatToggle.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public final class CommandEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("combattoggle")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> get(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("set")
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
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> resetCooldown(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("tag")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> tag(ctx, EntityArgument.getPlayer(ctx, "player"), CTConfig.combatTagSeconds.get()))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                                .executes(ctx -> tag(ctx,
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "seconds"))))))
                        .then(Commands.literal("untag")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> untag(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("reload")
                                .executes(CommandEvents::reload))
        );
    }

    private static int get(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CombatToggleData d = CombatToggleData.get(target);
        long now = System.currentTimeMillis();
        ctx.getSource().sendSuccess(() -> TextUtil.system("Mode=" + (d.enabled ? "COMBAT" : "PEACE") + " Tagged=" + d.isTagged(now)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String mode, boolean bypass) {
        boolean wantCombat;
        if (mode.equalsIgnoreCase("combat")) wantCombat = true;
        else if (mode.equalsIgnoreCase("peace")) wantCombat = false;
        else {
            ctx.getSource().sendFailure(TextUtil.system("Mode must be 'peace' or 'combat'"));
            return 0;
        }

        CombatToggleData d = CombatToggleData.get(target);
        long now = System.currentTimeMillis();

        if (!bypass || !CTConfig.allowAdminBypassCooldown.get()) {
            if (d.isCooldownActive(now, !wantCombat)) {
                long remaining = d.getRemainingCooldown(now);
                ctx.getSource().sendFailure(TextUtil.system("Cooldown active (" + TextUtil.formatRemaining(remaining) + " remaining). Use bypassCooldown=true if allowed."));
                return 0;
            }
        }

        d.enabled = wantCombat;
        if (CTConfig.cooldownTriggersOnToggle.get()) {
            d.lastToggleMs = now;
        }
        d.save(target);

        // Update scoreboard team for nameplate color
        TeamManager.updatePlayerTeam(target, d.enabled);

        ctx.getSource().sendSuccess(() -> TextUtil.system("Set " + target.getScoreboardName() + " to " + (wantCombat ? "COMBAT" : "PEACE")), true);
        target.sendSystemMessage(TextUtil.system("[Combat Toggle] Admin set your mode to: " + (wantCombat ? "COMBAT" : "PEACE")));

        PacketHandler.sendToPlayer(target, new S2CSyncStatePacket(d.enabled, d.lastToggleMs, d.combatTagUntilMs));
        return 1;
    }

    private static int resetCooldown(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CombatToggleData d = CombatToggleData.get(target);
        d.lastToggleMs = 0L;
        d.lastPvpMs = 0L;
        d.save(target);

        ctx.getSource().sendSuccess(() -> TextUtil.system("Cooldown reset for " + target.getScoreboardName()), true);
        PacketHandler.sendToPlayer(target, new S2CSyncStatePacket(d.enabled, d.lastToggleMs, d.combatTagUntilMs));
        return 1;
    }

    private static int tag(CommandContext<CommandSourceStack> ctx, ServerPlayer target, int seconds) {
        CombatToggleData d = CombatToggleData.get(target);
        long now = System.currentTimeMillis();
        long until = now + (seconds * 1000L);
        if (until > d.combatTagUntilMs) d.combatTagUntilMs = until;

        if (CTConfig.forceCombatWhileTagged.get()) {
            d.enabled = true;
            // Update team if forcing combat mode
            TeamManager.updatePlayerTeam(target, true);
        }

        d.save(target);

        ctx.getSource().sendSuccess(() -> TextUtil.system("Tagged " + target.getScoreboardName() + " for " + seconds + "s"), true);
        target.sendSystemMessage(TextUtil.system("[Combat Toggle] You have been combat-tagged for " + seconds + "s"));

        PacketHandler.sendToPlayer(target, new S2CSyncStatePacket(d.enabled, d.lastToggleMs, d.combatTagUntilMs));
        return 1;
    }

    private static int untag(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CombatToggleData d = CombatToggleData.get(target);
        d.combatTagUntilMs = 0L;
        d.save(target);

        ctx.getSource().sendSuccess(() -> TextUtil.system("Untagged " + target.getScoreboardName()), true);
        target.sendSystemMessage(TextUtil.system("[Combat Toggle] Combat tag cleared"));

        PacketHandler.sendToPlayer(target, new S2CSyncStatePacket(d.enabled, d.lastToggleMs, d.combatTagUntilMs));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        // Forge auto reloads config on file change. This command is mostly ceremonial unless you implement manual reload hooks.
        ctx.getSource().sendSuccess(() -> TextUtil.system("Config reload requested. If you edited the file, Forge should pick it up."), false);
        return 1;
    }
}
