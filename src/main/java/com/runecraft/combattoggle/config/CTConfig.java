package com.runecraft.combattoggle.config;

import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.data.CooldownScope;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

public final class CTConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    // Server config values
    public static final ForgeConfigSpec.IntValue cooldownSeconds;
    public static final ForgeConfigSpec.IntValue combatTagSeconds;

    public static final ForgeConfigSpec.BooleanValue requireBothCombatEnabled;
    public static final ForgeConfigSpec.BooleanValue forceCombatWhileTagged;
    public static final ForgeConfigSpec.BooleanValue allowToggleWhileTagged;
    public static final ForgeConfigSpec.BooleanValue allowAdminBypassCooldown;

    public static final ForgeConfigSpec.BooleanValue cooldownTriggersOnToggle;
    public static final ForgeConfigSpec.BooleanValue cooldownTriggersOnPvp;
    public static final ForgeConfigSpec.EnumValue<CooldownScope> cooldownScope;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> blockedDamageTypes;

    public static final ForgeConfigSpec.BooleanValue useEmojiPrefixes;
    public static final ForgeConfigSpec.ConfigValue<String> combatEmoji;
    public static final ForgeConfigSpec.ConfigValue<String> peaceEmoji;
    public static final ForgeConfigSpec.BooleanValue useNameplateColors;
    public static final ForgeConfigSpec.BooleanValue useScoreboardTeams;
    public static final ForgeConfigSpec.ConfigValue<String> combatTeamName;
    public static final ForgeConfigSpec.ConfigValue<String> peaceTeamName;

    // Client config values
    public static final ForgeConfigSpec.BooleanValue showHud;
    public static final ForgeConfigSpec.BooleanValue showHudTimers;
    public static final ForgeConfigSpec.EnumValue<HudAnchor> hudAnchor;
    public static final ForgeConfigSpec.IntValue hudYOffset;

    /** Horizontal HUD alignment, exposed in the client config so HUD-customisation users can move the indicator. */
    public enum HudAnchor { LEFT, CENTER, RIGHT }

    static {
        // Server config (per-world)
        ForgeConfigSpec.Builder sb = new ForgeConfigSpec.Builder();

        sb.comment("Combat Toggle server configuration").push("server");

        cooldownSeconds = sb
                .comment("Cooldown duration in seconds (default 600 = 10 minutes)")
                .defineInRange("cooldownSeconds", 600, 0, 86400);

        combatTagSeconds = sb
                .comment("Combat tag duration after PvP interaction in seconds (default 30)")
                .defineInRange("combatTagSeconds", 30, 0, 3600);

        requireBothCombatEnabled = sb
                .comment("If true, PvP only works when BOTH attacker and victim are in Combat mode. If false, only attacker matters.")
                .define("requireBothCombatEnabled", true);

        forceCombatWhileTagged = sb
                .comment("If true, players are forced into Combat mode while combat-tagged")
                .define("forceCombatWhileTagged", true);

        allowToggleWhileTagged = sb
                .comment("If true, players can still toggle while tagged. If false, toggling to Peace is denied while tagged.")
                .define("allowToggleWhileTagged", false);

        allowAdminBypassCooldown = sb
                .comment("If true, admins can bypass cooldown via commands")
                .define("allowAdminBypassCooldown", true);

        cooldownTriggersOnToggle = sb
                .comment("If true, cooldown starts when player toggles mode. If false, toggling is always instant (unless PvP-triggered cooldown is active)")
                .define("cooldownTriggersOnToggle", false);

        cooldownTriggersOnPvp = sb
                .comment("If true, cooldown starts when player deals or receives PvP damage. Recommended: true")
                .define("cooldownTriggersOnPvp", true);

        cooldownScope = sb
                .comment(
                        "Which mode-transition directions an active cooldown blocks.",
                        "  PEACE_ONLY (default): blocks Combat->Peace; Peace->Combat is always allowed (typical PvP-server config).",
                        "  COMBAT_ONLY:          blocks Peace->Combat; Combat->Peace is always allowed (good for safe-zone-friendly servers).",
                        "  BOTH:                 blocks both directions (locks the player into their current mode for the duration).",
                        "  NONE:                 cooldown is computed and reported but never blocks a transition."
                )
                .defineEnum("cooldownScope", CooldownScope.PEACE_ONLY);

        blockedDamageTypes = sb
                .comment(
                        "Damage-type resource locations always blocked between players, regardless of mode.",
                        "Useful to forbid specific PvP vectors while leaving the rest enabled.",
                        "Example: [\"minecraft:magic\", \"minecraft:indirect_magic\", \"minecraft:trident\"].",
                        "Empty list (default) blocks nothing extra; use the mode toggle for blanket PvP control."
                )
                .defineList("blockedDamageTypes", List.of(), e -> e instanceof String);

        sb.pop();

        sb.comment("Nameplate visual customization").push("nameplate");

        useEmojiPrefixes = sb
                .comment("If true, adds text/emoji prefixes to player nameplates")
                .define("useEmojiPrefixes", true);

        combatEmoji = sb
                .comment("Text/emoji prefix for Combat mode players. Only BMP Unicode (U+0000-U+FFFF) renders in Minecraft's font. Supplementary emoji (e.g. surrogate pairs) will show as garbled text.")
                .define("combatEmoji", "\u2694 ");

        peaceEmoji = sb
                .comment("Text/emoji prefix for Peace mode players. Only BMP Unicode (U+0000-U+FFFF) renders in Minecraft's font. Supplementary emoji (e.g. surrogate pairs) will show as garbled text.")
                .define("peaceEmoji", "\u2615 ");

        useNameplateColors = sb
                .comment("If true, uses scoreboard team colors (RED for Combat, BLUE for Peace)")
                .define("useNameplateColors", true);

        useScoreboardTeams = sb
                .comment("Use scoreboard teams for nameplate colors/prefixes. Disable to avoid conflicts with other mods.")
                .define("useScoreboardTeams", true);

        combatTeamName = sb
                .comment("Scoreboard team name for Combat mode players")
                .define("combatTeamName", "ct_combat");

        peaceTeamName = sb
                .comment("Scoreboard team name for Peace mode players")
                .define("peaceTeamName", "ct_peace");

        sb.pop();

        SERVER_SPEC = sb.build();

        // Client config (per-client)
        ForgeConfigSpec.Builder cb = new ForgeConfigSpec.Builder();
        cb.comment("Combat Toggle client configuration").push("client");
        showHud = cb.comment("Display the HUD mode indicator").define("showHud", true);
        showHudTimers = cb.comment("Display the combat-tag and cooldown countdown timers below the HUD indicator").define("showHudTimers", true);
        hudAnchor = cb
                .comment("Horizontal alignment of the HUD indicator: LEFT, CENTER, or RIGHT.")
                .defineEnum("hudAnchor", HudAnchor.CENTER);
        hudYOffset = cb
                .comment("Vertical offset of the HUD indicator from the top of the screen, in pixels.")
                .defineInRange("hudYOffset", 6, -1024, 1024);
        cb.pop();

        CLIENT_SPEC = cb.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, CombatToggle.MODID + "-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, CombatToggle.MODID + "-client.toml");
        CombatToggle.LOGGER.info("Combat Toggle config registered (server + client)");
    }
}
