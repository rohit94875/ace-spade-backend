package com.acespade.model;

import com.acespade.model.enums.Rank;
import com.acespade.model.enums.Suit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Card implements Serializable {
    private static final long serialVersionUID = 1L;

    private Suit suit;
    private Rank rank;

    /**
     * 0 or 1 — which physical deck this card comes from.
     * Used when two identical cards are played; playOrder is the actual tiebreaker.
     */
    private int deckIndex;

    /**
     * Position within the current trick (0 = played first, N-1 = played last).
     * Set by the game engine when the card is played; always 0 while in hand.
     */
    private int playOrder;

    /** Returns true if this card matches another by suit, rank, and deck copy. */
    public boolean matches(Card other) {
        return this.suit == other.suit
                && this.rank == other.rank
                && this.deckIndex == other.deckIndex;
    }
}
