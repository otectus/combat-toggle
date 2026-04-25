package com.runecraft.combattoggle.data;

/**
 * Which mode-transition directions an active cooldown should block.
 *
 * <p>Replaces the pre-1.2.0 {@code cooldownAppliesToPeaceOnly} boolean, which conflated
 * "PEACE_ONLY" and "BOTH" into one flag and gave operators no way to express the
 * symmetric "COMBAT_ONLY" or the rarely-useful "NONE" cases.
 */
public enum CooldownScope {
    /** Cooldown blocks both directions. */
    BOTH,
    /** Cooldown blocks only Combat→Peace transitions; Peace→Combat is always allowed. */
    PEACE_ONLY,
    /** Cooldown blocks only Peace→Combat transitions; Combat→Peace is always allowed. */
    COMBAT_ONLY,
    /** Cooldown is computed (and reported) but never blocks a transition. */
    NONE;
}
