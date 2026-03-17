package com.runecraft.combattoggle.config;

import com.runecraft.combattoggle.CombatToggle;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class CTConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue cooldownSeconds;
    public static final ForgeConfigSpec.IntValue combatTagSeconds;

    public static final ForgeConfigSpec.BooleanValue requireBothCombatEnabled;
    public static final ForgeConfigSpec.BooleanValue forceCombatWhileTagged;
    public static final ForgeConfigSpec.BooleanValue allowToggleWhileTagged;
    public static final ForgeConfigSpec.BooleanValue allowAdminBypassCooldown;

    // New PvP-triggered cooldown options
    public static final ForgeConfigSpec.BooleanValue cooldownTriggersOnToggle;
    public static final ForgeConfigSpec.BooleanValue cooldownTriggersOnPvp;
    public static final ForgeConfigSpec.BooleanValue cooldownAppliesToPeaceOnly;

    // Nameplate emoji prefix options
    public static final ForgeConfigSpec.BooleanValue useEmojiPrefixes;
    public static final ForgeConfigSpec.ConfigValue<String> combatEmoji;
    public static final ForgeConfigSpec.ConfigValue<String> peaceEmoji;
    public static final ForgeConfigSpec.BooleanValue useNameplateColors;

    public static final ForgeConfigSpec.BooleanValue showHud;
    public static final ForgeConfigSpec.BooleanValue allowClientButtonClick;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Combat Toggle server configuration").push("server");

        cooldownSeconds = b
                .comment("Cooldown duration in seconds (default 600 = 10 minutes)")
                .defineInRange("cooldownSeconds", 600, 0, 86400);

        combatTagSeconds = b
                .comment("Combat tag duration after PvP interaction in seconds (default 30)")
                .defineInRange("combatTagSeconds", 30, 0, 3600);

        requireBothCombatEnabled = b
                .comment("If true, PvP only works when BOTH attacker and victim are in Combat mode. If false, only attacker matters.")
                .define("requireBothCombatEnabled", true);

        forceCombatWhileTagged = b
                .comment("If true, players are forced into Combat mode while combat-tagged")
                .define("forceCombatWhileTagged", true);

        allowToggleWhileTagged = b
                .comment("If true, players can still toggle while tagged. If false, toggling to Peace is denied while tagged.")
                .define("allowToggleWhileTagged", false);

        allowAdminBypassCooldown = b
                .comment("If true, admins can bypass cooldown via commands")
                .define("allowAdminBypassCooldown", true);

        cooldownTriggersOnToggle = b
                .comment("If true, cooldown starts when player toggles mode. If false, toggling is always instant (unless PvP-triggered cooldown is active)")
                .define("cooldownTriggersOnToggle", false);

        cooldownTriggersOnPvp = b
                .comment("If true, cooldown starts when player deals or receives PvP damage. Recommended: true")
                .define("cooldownTriggersOnPvp", true);

        cooldownAppliesToPeaceOnly = b
                .comment("If true, cooldown only prevents toggling TO Peace mode (Combat->Peace blocked, Peace->Combat always allowed). If false, cooldown blocks both directions.")
                .define("cooldownAppliesToPeaceOnly", true);

        b.pop();

        b.comment("Nameplate visual customization").push("nameplate");

        useEmojiPrefixes = b
                .comment("If true, adds text/emoji prefixes to player nameplates")
                .define("useEmojiPrefixes", true);

        combatEmoji = b
                .comment("Text/emoji prefix for Combat mode players (default: [PVP] with space). Use Unicode escapes for emojis: \\u2694 for crossed swords")
                .define("combatEmoji", "[PVP] ");

        peaceEmoji = b
                .comment("Text/emoji prefix for Peace mode players (default: [PEACE] with space). Use Unicode escapes for emojis: \\uD83D\\uDEE1 for shield")
                .define("peaceEmoji", "[PEACE] ");

        useNameplateColors = b
                .comment("If true, uses scoreboard team colors (RED for Combat, BLUE for Peace)")
                .define("useNameplateColors", true);

        b.pop();

        b.comment("Client-facing toggles that are still controlled by server config").push("client");
        showHud = b.define("showHud", true);
        allowClientButtonClick = b.define("allowClientButtonClick", true);
        b.pop();

        SPEC = b.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, CombatToggle.MODID + ".toml");
    }
}
