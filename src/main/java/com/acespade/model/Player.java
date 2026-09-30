package com.acespade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String username;

    /** Linked account for ranked play; null for guests. */
    private Long userId;

    @Builder.Default
    private List<Card> hand = new ArrayList<>();

    /** Null until the player has placed their bid for the current round. */
    private Integer bid;

    private int tricksWon;

    /** True for BOT Vitality (solo or takeover). */
    private boolean bot;

    /** WebSocket connected right now. */
    @Builder.Default
    private boolean connected = false;

    /** Epoch millis of last presence change. */
    private long lastSeenAt;

    /** When disconnect grace expires; null if not in grace. */
    private Long graceExpiresAt;

    /** Epoch millis when the player went away (backgrounded / dropped); null when present. */
    private Long awaySince;

    /** Number of times a turn was auto-played for this player because they were away. */
    private int autoPlayCount;

    /** Lobby ready-up before host can start. */
    @Builder.Default
    private boolean ready = false;

    /** Clan Battle: 1 = team blue, 2 = team red. */
    private Integer teamId;

    /** Poker: chip stack. */
    @Builder.Default
    private int chips = 0;

    /** Poker: chips committed this betting street. */
    @Builder.Default
    private int betThisStreet = 0;

    /** Poker: chips committed this hand (all streets). */
    @Builder.Default
    private int totalBetThisHand = 0;

    /** Poker: folded this hand. */
    @Builder.Default
    private boolean folded = false;

    /** Poker: put all remaining chips in (no further betting). */
    @Builder.Default
    private boolean allIn = false;

    /** Poker: already acted this street since last aggressor. */
    @Builder.Default
    private boolean actedThisStreet = false;

    public void resetForRound() {
        this.hand = new ArrayList<>();
        this.bid = null;
        this.tricksWon = 0;
    }

    public void resetForPokerHand() {
        this.hand = new ArrayList<>();
        this.betThisStreet = 0;
        this.totalBetThisHand = 0;
        this.folded = false;
        this.allIn = false;
        this.actedThisStreet = false;
    }
}
