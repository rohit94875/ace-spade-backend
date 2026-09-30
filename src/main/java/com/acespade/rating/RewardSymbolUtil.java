package com.acespade.rating;

import com.acespade.model.enums.RewardSymbolType;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

public final class RewardSymbolUtil {

    private static final Set<RewardSymbolType> TIER_CARDS = EnumSet.of(
            RewardSymbolType.SAND_CARD,
            RewardSymbolType.BRONZE_CARD,
            RewardSymbolType.SILVER_CARD,
            RewardSymbolType.GOLD_CARD,
            RewardSymbolType.PLATINUM_CARD,
            RewardSymbolType.DIAMOND_CARD,
            RewardSymbolType.ACE_CARD
    );

    private static final Set<RewardSymbolType> AWARD_BADGES = EnumSet.of(
            RewardSymbolType.TOP_MMR,
            RewardSymbolType.MOST_WINS,
            RewardSymbolType.WIN_STREAK,
            RewardSymbolType.FINISHER,
            RewardSymbolType.MOST_MATCHES,
            RewardSymbolType.MOST_LOSSES,
            RewardSymbolType.LOSS_STREAK,
            RewardSymbolType.BID_MASTER
    );

    /** Higher prestige first (Top MMR at top). */
    public static final Comparator<RewardSymbolType> AWARD_PRESTIGE_ORDER = Comparator
            .comparingInt(RewardSymbolUtil::awardPrestigeRank);

    private RewardSymbolUtil() {}

    public static boolean isTierCard(RewardSymbolType type) {
        return TIER_CARDS.contains(type);
    }

    public static boolean isAwardBadge(RewardSymbolType type) {
        return AWARD_BADGES.contains(type);
    }

    public static int awardPrestigeRank(RewardSymbolType type) {
        switch (type) {
            case TOP_MMR: return 0;
            case MOST_WINS: return 1;
            case WIN_STREAK: return 2;
            case FINISHER: return 3;
            case MOST_MATCHES: return 4;
            case MOST_LOSSES: return 5;
            case LOSS_STREAK: return 6;
            case BID_MASTER: return 7;
            default: return 99;
        }
    }
}
