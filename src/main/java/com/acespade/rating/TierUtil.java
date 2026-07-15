package com.acespade.rating;

/**
 * Tier thresholds for ranked MMR (Season 1).
 */
public final class TierUtil {

    public static final int CASUAL_MAX_ROUNDS = 5;
    public static final int RANKED_MIN_ROUNDS = 8;
    public static final int RANKED_MAX_ROUNDS = 13;
    public static final int PLACEMENT_GAMES_REQUIRED = 3;
    public static final int CURRENT_SEASON_ID = 1;

    private TierUtil() {}

    public static String tierForMmr(double mmr) {
        if (mmr < 1050) return "Please Don't Play";

        if (mmr < 1100) return "Sand 3";
        if (mmr < 1150) return "Sand 2";
        if (mmr < 1200) return "Sand 1";

        if (mmr < 1250) return "Bronze 3";
        if (mmr < 1300) return "Bronze 2";
        if (mmr < 1350) return "Bronze 1";

        if (mmr < 1400) return "Silver 3";
        if (mmr < 1450) return "Silver 2";
        if (mmr < 1500) return "Silver 1";

        if (mmr < 1550) return "Gold 3";
        if (mmr < 1600) return "Gold 2";
        if (mmr < 1650) return "Gold 1";

        if (mmr < 1700) return "Platinum 3";
        if (mmr < 1750) return "Platinum 2";
        if (mmr < 1800) return "Platinum 1";

        if (mmr < 1850) return "Diamond 3";
        if (mmr < 1900) return "Diamond 2";
        if (mmr < 1950) return "Diamond 1";

        if (mmr < 1975) return "Master";
        if (mmr < 2000) return "ACE KING";

        return "Challenger";
    }

    public static boolean isPlacementComplete(int placementGames) {
        return placementGames >= PLACEMENT_GAMES_REQUIRED;
    }

    /** Tier badge only after placement; MMR always visible. */
    public static String tierBadge(int placementGames, double mmr) {
        return isPlacementComplete(placementGames) ? tierForMmr(mmr) : null;
    }
}
