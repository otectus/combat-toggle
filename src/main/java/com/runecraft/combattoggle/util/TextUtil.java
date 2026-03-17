package com.runecraft.combattoggle.util;

import net.minecraft.network.chat.Component;

public final class TextUtil {
    public static Component system(String s) {
        return Component.literal(s);
    }

    public static String formatRemaining(long ms) {
        if (ms <= 0) return "00:00";
        long totalSec = ms / 1000L;
        long m = totalSec / 60L;
        long s = totalSec % 60L;
        return String.format("%02d:%02d", m, s);
    }
}
