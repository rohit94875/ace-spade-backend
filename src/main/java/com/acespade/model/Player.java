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

    @Builder.Default
    private List<Card> hand = new ArrayList<>();

    /** Null until the player has placed their bid for the current round. */
    private Integer bid;

    private int tricksWon;

  /** True for BOT Vitality (solo or takeover). */
    private boolean bot;

    public void resetForRound() {
        this.hand = new ArrayList<>();
        this.bid = null;
        this.tricksWon = 0;
    }
}
