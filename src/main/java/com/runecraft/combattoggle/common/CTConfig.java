package com.runecraft.combattoggle.common;

import com.runecraft.combattoggle.CombatToggle;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Server + client config split. SERVER_SPEC governs gameplay rules (cooldowns, tag, PvP enforcement,
 * scoreboard teams, emoji prefixes); CLIENT_SPEC governs the HUD's visual placement and visibility.
 *
 * <p>Server-side keys are carried over verbatim from 1.1.0 to preserve operator config files across
 * the upgrade. Client-side HUD keys are new in 1.2.0 (1.1.0 only had the now-dropped {@code showHud}
 * server option).
 *
 * <p>This class has no {@code net.minecraft.client.*} imports; safe to load on a dedicated server.
 * The {@link HudAnchor} enum referenced by {@link #hudAnchor} lives in the same package and is also
 * a plain POJO.
 */
public final class CTConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    // ----- Server: combat & tagging
    public static final ForgeConfigSpec.IntValue cooldownSeconds;
    public static final ForgeConfigSpec.IntValue combatTagSeconds;
    public static final ForgeConfigSpec.BooleanValue requireBothCombatEnabled;
    public static final ForgeConfigSpec.BooleanValue forceCombatWhileTagged;
    public static final ForgeConfigSpec.BooleanValue allowToggleWhileTagged;
    public static final ForgeConfigSpec.BooleanValue allowAdminBypassCooldown;
    public static final ForgeConfigSpec.BooleanValue cooldownTriggersOnToggle;
    public static final ForgeConfigSpec.BooleanValue cooldownTriggersOnPvp;
    public static final ForgeConfigSpec.BooleanValue cooldownAppliesToPeaceOnly;

    // ----- Server: nameplate
    public static final ForgeConfigSpec.BooleanValue useEmojiPrefixes;
    public static final ForgeConfigSpec.ConfigValue<String> combatEmoji;
    public static final ForgeConfigSpec.ConfigValue<String> peaceEmoji;
    public static final ForgeConfigSpec.BooleanValue useNameplateColors;
    public static final ForgeConfigSpec.BooleanValue useScoreboardTeams;
    public static final ForgeConfigSpec.ConfigValue<String> combatTeamName;
    public static final ForgeConfigSpec.ConfigValue<String> peaceTeamName;

    // ----- Client: HUD
    public static final ForgeConfigSpec.BooleanValue hudEnabled;
    public static final ForgeConfigSpec.BooleanValue hudShowInPeaceMode;
    public static final ForgeConfigSpec.BooleanValue hudShowInCombatMode;
    public static final ForgeConfigSpec.EnumValue<HudAnchor> hudAnchor;
    public static final ForgeConfigSpec.IntValue hudOffsetX;
    public static final ForgeConfigSpec.IntValue hudOffsetY;
    public static final ForgeConfigSpec.DoubleValue hudScale;
    public static final ForgeConfigSpec.BooleanValue hudShowTimers;

    static {
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
                .comment("If true, admins can bypass cooldown via /combattoggle set ... bypass=true")
                .define("allowAdminBypassCooldown", true);
        cooldownTriggersOnToggle = sb
                .comment("If true, cooldown starts when player toggles mode")
                .define("cooldownTriggersOnToggle", false);
        cooldownTriggersOnPvp = sb
                .comment("If true, cooldown starts when player deals or receives PvP damage. Recommended: true")
                .define("cooldownTriggersOnPvp", true);
        cooldownAppliesToPeaceOnly = sb
                .comment("If true (1.1.0 default), an active cooldown only blocks Combat -> Peace transitions; switching INTO Combat is always allowed. If false, the cooldown blocks toggles in either direction.")
                .define("cooldownAppliesToPeaceOnly", true);

        sb.pop();

        sb.comment("Nameplate visual customization").push("nameplate");
        useEmojiPrefixes = sb
                .comment("If true, adds text/emoji prefixes to player nameplates")
                .define("useEmojiPrefixes", true);
        combatEmoji = sb
                .comment("Text/emoji prefix for Combat mode players. Only BMP Unicode (U+0000-U+FFFF) renders in Minecraft's font.")
                .define("combatEmoji", "⚔ ");
        peaceEmoji = sb
                .comment("Text/emoji prefix for Peace mode players. Only BMP Unicode (U+0000-U+FFFF) renders in Minecraft's font.")
                .define("peaceEmoji", "☕ ");
        useNameplateColors = sb
                .comment("If true, uses scoreboard team colors (RED for Combat, BLUE for Peace)")
                .define("useNameplateColors", true);
        useScoreboardTeams = sb
                .comment("Use scoreboard teams for nameplate colors/prefixes. Disable to avoid conflicts with other team-managing mods.")
                .define("useScoreboardTeams", true);
        combatTeamName = sb
                .comment("Scoreboard team name for Combat mode players")
                .define("combatTeamName", "ct_combat");
        peaceTeamName = sb
                .comment("Scoreboard team name for Peace mode players")
                .define("peaceTeamName", "ct_peace");
        sb.pop();

        SERVER_SPEC = sb.build();

        ForgeConfigSpec.Builder cb = new ForgeConfigSpec.Builder();
        cb.comment("Combat Toggle client configuration (HUD-only; gameplay rules live in the server config)").push("client");
        hudEnabled = cb
                .comment("Master switch for the HUD mode indicator")
                .define("hudEnabled", true);
        hudShowInCombatMode = cb
                .comment("Show the HUD while the player is in Combat mode")
                .define("hudShowInCombatMode", true);
        hudShowInPeaceMode = cb
                .comment("Show the HUD while the player is in Peace mode")
                .define("hudShowInPeaceMode", true);
        hudAnchor = cb
                .comment(
                        "HUD anchor on the screen. One of:",
                        "  TOP_LEFT, TOP_CENTER, TOP_RIGHT,",
                        "  CENTER_LEFT, CENTER, CENTER_RIGHT,",
                        "  BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,",
                        "  CUSTOM (treats hudOffsetX/Y as absolute screen coordinates)."
                )
                .defineEnum("hudAnchor", HudAnchor.TOP_CENTER);
        hudOffsetX = cb
                .comment("Horizontal pixel offset from the anchor (inward). Absolute screen X if hudAnchor=CUSTOM.")
                .defineInRange("hudOffsetX", 0, -8192, 8192);
        hudOffsetY = cb
                .comment("Vertical pixel offset from the anchor (inward). Absolute screen Y if hudAnchor=CUSTOM.")
                .defineInRange("hudOffsetY", 6, -8192, 8192);
        hudScale = cb
                .comment("Render scale for the HUD (clamped to [0.5, 3.0])")
                .defineInRange("hudScale", 1.0, 0.5, 3.0);
        hudShowTimers = cb
                .comment("Display the combat-tag and cooldown countdown timers below the HUD indicator")
                .define("hudShowTimers", true);
        cb.pop();

        CLIENT_SPEC = cb.build();
    }

    public static void register(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, CombatToggle.MODID + "-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, CombatToggle.MODID + "-client.toml");
        CombatToggle.LOGGER.info("Combat Toggle config registered (server + client)");
    }

    private CTConfig() {}
}
