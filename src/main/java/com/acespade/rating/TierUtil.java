package com.acespade.rating;

/**
 * Tier thresholds for ranked MMR (Season 1).
 */
public final class TierUtil {

    public static final int PLACEMENT_GAMES_REQUIRED = 5;
    public static final int CURRENT_SEASON_ID = 1;

    private TierUtil() {}

    public static String tierForMmr(double mmr) {
        if (mmr < 1200) return "Bronze";
        if (mmr < 1400) return "Silver";
        if (mmr < 1600) return "Gold";
        if (mmr < 1800) return "Platinum";
        return "Diamond";
    }

    public static boolean isPlacementComplete(int placementGames) {
        return placementGames >= PLACEMENT_GAMES_REQUIRED;
    }

    /** Tier badge only after placement; MMR always visible. */
    public static String tierBadge(int placementGames, double mmr) {
        return isPlacementComplete(placementGames) ? tierForMmr(mmr) : null;
    }
}
