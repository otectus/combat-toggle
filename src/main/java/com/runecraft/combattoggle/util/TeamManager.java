package com.runecraft.combattoggle.util;

import com.runecraft.combattoggle.config.CTConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Manages scoreboard teams for visual Combat Toggle state indication.
 * Uses vanilla scoreboard teams to color player nameplates and add emoji prefixes:
 * - Combat mode: RED nameplate with ⚔ prefix (configurable)
 * - Peace mode: BLUE nameplate with 🛡 prefix (configurable)
 */
public final class TeamManager {
    private static String getCombatTeam() { return CTConfig.combatTeamName.get(); }
    private static String getPeaceTeam() { return CTConfig.peaceTeamName.get(); }

    private TeamManager() {}

    /**
     * Ensures both Combat Toggle teams exist on the scoreboard.
     * Creates them if they don't exist, with proper formatting.
     * Applies emoji prefixes and colors based on configuration.
     */
    public static void ensureTeamsExist(Scoreboard scoreboard) {
        if (!CTConfig.useScoreboardTeams.get()) return;

        PlayerTeam combat = scoreboard.getPlayerTeam(getCombatTeam());
        if (combat == null) {
            combat = scoreboard.addPlayerTeam(getCombatTeam());
        }
        
        // Apply color if enabled
        if (CTConfig.useNameplateColors.get()) {
            combat.setColor(ChatFormatting.RED);
        } else {
            combat.setColor(ChatFormatting.RESET);
        }
        
        // Apply emoji prefix if enabled
        if (CTConfig.useEmojiPrefixes.get()) {
            String emoji = CTConfig.combatEmoji.get();
            combat.setPlayerPrefix(Component.literal(emoji));
        } else {
            combat.setPlayerPrefix(Component.empty());
        }
        
        combat.setAllowFriendlyFire(true);
        combat.setSeeFriendlyInvisibles(false);

        PlayerTeam peace = scoreboard.getPlayerTeam(getPeaceTeam());
        if (peace == null) {
            peace = scoreboard.addPlayerTeam(getPeaceTeam());
        }
        
        // Apply color if enabled
        if (CTConfig.useNameplateColors.get()) {
            peace.setColor(ChatFormatting.BLUE);
        } else {
            peace.setColor(ChatFormatting.RESET);
        }
        
        // Apply emoji prefix if enabled
        if (CTConfig.useEmojiPrefixes.get()) {
            String emoji = CTConfig.peaceEmoji.get();
            peace.setPlayerPrefix(Component.literal(emoji));
        } else {
            peace.setPlayerPrefix(Component.empty());
        }
        
        peace.setAllowFriendlyFire(true);
        peace.setSeeFriendlyInvisibles(false);
    }

    /**
     * Assigns the player to the appropriate team based on combat state.
     * Removes from previous team first to avoid conflicts.
     * 
     * @param player The player to assign
     * @param combatEnabled true for combat team (red), false for peace team (blue)
     */
    public static void updatePlayerTeam(ServerPlayer player, boolean combatEnabled) {
        if (!CTConfig.useScoreboardTeams.get()) {
            removePlayerFromTeams(player);
            return;
        }

        Scoreboard scoreboard = player.getScoreboard();
        ensureTeamsExist(scoreboard);

        String playerName = player.getScoreboardName();

        // Remove from CT teams only (avoid disrupting other mods' teams)
        PlayerTeam currentTeam = scoreboard.getPlayersTeam(playerName);
        if (currentTeam != null && (getCombatTeam().equals(currentTeam.getName()) || getPeaceTeam().equals(currentTeam.getName()))) {
            scoreboard.removePlayerFromTeam(playerName, currentTeam);
        }

        // Assign to appropriate team
        PlayerTeam targetTeam = scoreboard.getPlayerTeam(combatEnabled ? getCombatTeam() : getPeaceTeam());
        if (targetTeam != null) {
            scoreboard.addPlayerToTeam(playerName, targetTeam);
        }
    }

    /**
     * Removes the player from Combat Toggle teams.
     * Useful for cleanup or when another system needs to manage teams.
     */
    public static void removePlayerFromTeams(ServerPlayer player) {
        Scoreboard scoreboard = player.getScoreboard();
        String playerName = player.getScoreboardName();
        PlayerTeam currentTeam = scoreboard.getPlayersTeam(playerName);
        if (currentTeam != null && (getCombatTeam().equals(currentTeam.getName()) || getPeaceTeam().equals(currentTeam.getName()))) {
            scoreboard.removePlayerFromTeam(playerName, currentTeam);
        }
    }
}
