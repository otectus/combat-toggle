package com.runecraft.combattoggle.server;

import net.minecraft.network.chat.Component;

/** Server-side chat-formatting helpers. No client-class imports. */
public final class TextHelper {

    public static String formatRemaining(long ms) {
        if (ms <= 0) return "00:00";
        long totalSec = ms / 1000L;
        long m = totalSec / 60L;
        long s = totalSec % 60L;
        return String.format("%02d:%02d", m, s);
    }

    public static Component modeName(boolean inCombat) {
        return inCombat
                ? Component.translatable("combattoggle.msg.mode_combat")
                : Component.translatable("combattoggle.msg.mode_peace");
    }

    private TextHelper() {}
}
