package com.acespade.service;

import com.acespade.model.Card;
import com.acespade.model.enums.Rank;
import com.acespade.model.enums.Suit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Best 5-card hand from 5–7 cards. Higher long score wins.
 * Encoding: category * 13^5 + kicker0 * 13^4 + … (category 0–8).
 */
@Component
public class PokerHandEvaluator {

    public static final int HIGH_CARD = 0;
    public static final int ONE_PAIR = 1;
    public static final int TWO_PAIR = 2;
    public static final int THREE_KIND = 3;
    public static final int STRAIGHT = 4;
    public static final int FLUSH = 5;
    public static final int FULL_HOUSE = 6;
    public static final int FOUR_KIND = 7;
    public static final int STRAIGHT_FLUSH = 8;

    private static final String[] NAMES = {
            "High Card", "Pair", "Two Pair", "Three of a Kind", "Straight",
            "Flush", "Full House", "Four of a Kind", "Straight Flush"
    };

    public long evaluate(List<Card> hole, List<Card> community) {
        List<Card> all = new ArrayList<>(hole.size() + community.size());
        all.addAll(hole);
        all.addAll(community);
        if (all.size() < 5) {
            throw new IllegalArgumentException("Need at least 5 cards");
        }
        long best = -1;
        int n = all.size();
        for (int a = 0; a < n - 4; a++) {
            for (int b = a + 1; b < n - 3; b++) {
                for (int c = b + 1; c < n - 2; c++) {
                    for (int d = c + 1; d < n - 1; d++) {
                        for (int e = d + 1; e < n; e++) {
                            best = Math.max(best, scoreFive(Arrays.asList(
                                    all.get(a), all.get(b), all.get(c), all.get(d), all.get(e))));
                        }
                    }
                }
            }
        }
        return best;
    }

    public int category(long score) {
        return (int) (score / 371293L); // 13^5
    }

    public String categoryName(long score) {
        int cat = category(score);
        if (cat < 0 || cat >= NAMES.length) {
            return "Unknown";
        }
        return NAMES[cat];
    }

    long scoreFive(List<Card> five) {
        int[] ranks = new int[5];
        Suit[] suits = new Suit[5];
        for (int i = 0; i < 5; i++) {
            ranks[i] = five.get(i).getRank().ordinal();
            suits[i] = five.get(i).getSuit();
        }
        Arrays.sort(ranks);

        boolean flush = suits[0] == suits[1] && suits[1] == suits[2]
                && suits[2] == suits[3] && suits[3] == suits[4];
        int straightHigh = straightHigh(ranks);

        int[] counts = new int[13];
        for (int r : ranks) {
            counts[r]++;
        }
        List<Integer> fours = new ArrayList<>();
        List<Integer> threes = new ArrayList<>();
        List<Integer> pairs = new ArrayList<>();
        List<Integer> singles = new ArrayList<>();
        for (int r = 12; r >= 0; r--) {
            if (counts[r] == 4) fours.add(r);
            else if (counts[r] == 3) threes.add(r);
            else if (counts[r] == 2) pairs.add(r);
            else if (counts[r] == 1) singles.add(r);
        }

        if (flush && straightHigh >= 0) {
            return pack(STRAIGHT_FLUSH, straightHigh, 0, 0, 0, 0);
        }
        if (!fours.isEmpty()) {
            return pack(FOUR_KIND, fours.get(0), singles.isEmpty() ? 0 : singles.get(0), 0, 0, 0);
        }
        if (!threes.isEmpty() && !pairs.isEmpty()) {
            return pack(FULL_HOUSE, threes.get(0), pairs.get(0), 0, 0, 0);
        }
        if (flush) {
            return pack(FLUSH, ranks[4], ranks[3], ranks[2], ranks[1], ranks[0]);
        }
        if (straightHigh >= 0) {
            return pack(STRAIGHT, straightHigh, 0, 0, 0, 0);
        }
        if (!threes.isEmpty()) {
            return pack(THREE_KIND, threes.get(0),
                    singles.size() > 0 ? singles.get(0) : 0,
                    singles.size() > 1 ? singles.get(1) : 0, 0, 0);
        }
        if (pairs.size() >= 2) {
            return pack(TWO_PAIR, pairs.get(0), pairs.get(1),
                    singles.isEmpty() ? 0 : singles.get(0), 0, 0);
        }
        if (pairs.size() == 1) {
            return pack(ONE_PAIR, pairs.get(0),
                    singles.size() > 0 ? singles.get(0) : 0,
                    singles.size() > 1 ? singles.get(1) : 0,
                    singles.size() > 2 ? singles.get(2) : 0, 0);
        }
        return pack(HIGH_CARD, ranks[4], ranks[3], ranks[2], ranks[1], ranks[0]);
    }

    private int straightHigh(int[] sortedAsc) {
        if (sortedAsc[0] + 1 == sortedAsc[1]
                && sortedAsc[1] + 1 == sortedAsc[2]
                && sortedAsc[2] + 1 == sortedAsc[3]
                && sortedAsc[3] + 1 == sortedAsc[4]) {
            return sortedAsc[4];
        }
        // A-2-3-4-5 wheel
        if (sortedAsc[0] == 0 && sortedAsc[1] == 1 && sortedAsc[2] == 2
                && sortedAsc[3] == 3 && sortedAsc[4] == Rank.ACE.ordinal()) {
            return 3;
        }
        return -1;
    }

    private long pack(int category, int a, int b, int c, int d, int e) {
        return ((((category * 13L + a) * 13 + b) * 13 + c) * 13 + d) * 13 + e;
    }
}
