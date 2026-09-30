package com.acespade.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum GameMode {
    CLASSIC,
    RUTHLESS_HIDDEN,
    CLAN_BATTLE,
    POKER;

    /** Modes that can run ranked and have their own MMR / season rewards. */
    public static List<GameMode> rankedModes() {
        return Arrays.asList(CLASSIC, RUTHLESS_HIDDEN);
    }

    public boolean isRankedEligible() {
        return this == CLASSIC || this == RUTHLESS_HIDDEN;
    }

    public static GameMode parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return CLASSIC;
        }
        return GameMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Resolve a ranked ladder mode. Clan Battle and unknown values fall back to Classic
     * for profile defaults; callers that need strict validation should use requireRankedMode.
     */
    public static String normalizeRankedMode(String raw) {
        GameMode mode = parse(raw);
        return mode.isRankedEligible() ? mode.name() : CLASSIC.name();
    }

    public static String requireRankedMode(String raw) {
        GameMode mode = parse(raw);
        if (!mode.isRankedEligible()) {
            throw new IllegalArgumentException(
                    "Leaderboard/rewards only support CLASSIC and RUTHLESS_HIDDEN, got: " + raw);
        }
        return mode.name();
    }
}
