package com.runecraft.combattoggle.data;

/**
 * Direction of an in-flight or hypothetical mode change. Replaces the historical
 * {@code boolean wantPeace} parameter so call sites read self-documenting at the use point.
 */
public enum ToggleDirection {
    TO_COMBAT,
    TO_PEACE;

    /** {@link #TO_COMBAT} if {@code wantCombat} is true, otherwise {@link #TO_PEACE}. */
    public static ToggleDirection toward(boolean wantCombat) {
        return wantCombat ? TO_COMBAT : TO_PEACE;
    }

    /** {@link #TO_COMBAT} if currently in Peace, {@link #TO_PEACE} if currently in Combat. */
    public static ToggleDirection nextFor(boolean currentlyInCombat) {
        return currentlyInCombat ? TO_PEACE : TO_COMBAT;
    }
}
