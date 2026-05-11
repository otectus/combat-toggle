package com.runecraft.combattoggle.server;

import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.common.CTConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import static com.runecraft.combattoggle.CombatToggle.MODID;

/**
 * Scoreboard-team management for nameplate visuals (RED for Combat, BLUE for Peace, plus optional
 * emoji prefixes). The team-name pair is snapshotted at config load/reload so per-toggle work skips
 * a config read.
 */
public final class TeamManager {
    private static volatile String cachedCombatTeam = "ct_combat";
    private static volatile String cachedPeaceTeam = "ct_peace";

    private TeamManager() {}

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Cache {
        @SubscribeEvent
        public static void onConfigLoadOrReload(ModConfigEvent event) {
            if (event.getConfig().getSpec() != CTConfig.SERVER_SPEC) return;
            cachedCombatTeam = CTConfig.combatTeamName.get();
            cachedPeaceTeam = CTConfig.peaceTeamName.get();
        }
    }

    public static void ensureTeamsExist(Scoreboard scoreboard) {
        if (!CTConfig.useScoreboardTeams.get()) return;
        if (cachedCombatTeam.equals(cachedPeaceTeam)) {
            CombatToggle.LOGGER.warn("Combat and peace team names are identical: '{}'. Scoreboard teams disabled to avoid confusion.", cachedCombatTeam);
            return;
        }
        applyTeamStyle(scoreboard, cachedCombatTeam, ChatFormatting.RED, CTConfig.combatEmoji.get());
        applyTeamStyle(scoreboard, cachedPeaceTeam, ChatFormatting.BLUE, CTConfig.peaceEmoji.get());
    }

    private static void applyTeamStyle(Scoreboard scoreboard, String teamName, ChatFormatting color, String emoji) {
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) team = scoreboard.addPlayerTeam(teamName);

        team.setColor(CTConfig.useNameplateColors.get() ? color : ChatFormatting.RESET);
        team.setPlayerPrefix(CTConfig.useEmojiPrefixes.get() ? Component.literal(emoji) : Component.empty());
        team.setAllowFriendlyFire(true);
        team.setSeeFriendlyInvisibles(false);
    }

    public static void updatePlayerTeam(ServerPlayer player, boolean combatEnabled) {
        if (!CTConfig.useScoreboardTeams.get()) {
            removePlayerFromTeams(player);
            return;
        }
        if (cachedCombatTeam.equals(cachedPeaceTeam)) return;

        Scoreboard scoreboard = player.getScoreboard();
        ensureTeamsExist(scoreboard);

        String name = player.getScoreboardName();

        // Only disturb our own teams — leave assignments by other team-managing mods alone.
        PlayerTeam current = scoreboard.getPlayersTeam(name);
        if (current != null && (cachedCombatTeam.equals(current.getName()) || cachedPeaceTeam.equals(current.getName()))) {
            scoreboard.removePlayerFromTeam(name, current);
        }

        String targetName = combatEnabled ? cachedCombatTeam : cachedPeaceTeam;
        PlayerTeam target = scoreboard.getPlayerTeam(targetName);
        if (target != null) {
            scoreboard.addPlayerToTeam(name, target);
            CombatToggle.LOGGER.debug("Assigned {} to team {}", name, targetName);
        }
    }

    public static void removePlayerFromTeams(ServerPlayer player) {
        Scoreboard scoreboard = player.getScoreboard();
        String name = player.getScoreboardName();
        PlayerTeam current = scoreboard.getPlayersTeam(name);
        if (current != null && (cachedCombatTeam.equals(current.getName()) || cachedPeaceTeam.equals(current.getName()))) {
            scoreboard.removePlayerFromTeam(name, current);
        }
    }
}
