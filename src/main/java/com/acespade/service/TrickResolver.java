package com.acespade.service;

import com.acespade.model.TrickCard;
import com.acespade.model.enums.Suit;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolves which player wins a completed trick.
 *
 * Suit trump hierarchy (by enum ordinal): SPADES(3) > CLUBS(2) > HEARTS(1) > DIAMONDS(0).
 * A card is "active" (can win) if its suit ordinal >= lead suit ordinal.
 * Among active cards: highest suit wins; ties on suit resolved by highest rank;
 * ties on rank (duplicate cards from 2-deck) resolved by play order (later = wins).
 */
@Service
public class TrickResolver {

    /**
     * Returns the TrickCard that wins the trick.
     * The list must be non-empty and ordered by playOrder (0 = first played).
     */
    public TrickCard resolveWinner(List<TrickCard> trick) {
        if (trick.isEmpty()) {
            throw new IllegalArgumentException("Cannot resolve empty trick");
        }

        Suit leadSuit = trick.get(0).getCard().getSuit();

        List<TrickCard> activeCards = trick.stream()
                .filter(tc -> isActive(tc.getCard().getSuit(), leadSuit))
                .collect(Collectors.toList());

        // Comparator: highest suit > highest rank > latest played (for duplicate card tie)
        Comparator<TrickCard> comparator = Comparator
                .comparingInt((TrickCard tc) -> tc.getCard().getSuit().ordinal())
                .thenComparingInt(tc -> tc.getCard().getRank().ordinal())
                .thenComparingInt(TrickCard::getPlayOrder);

        Optional<TrickCard> winner = activeCards.stream().max(comparator);
        return winner.orElse(trick.get(0));
    }

    /**
     * A card with the given suit is "active" (can contribute to winning the trick)
     * if its suit ordinal is >= the lead suit ordinal.
     * This implements the full trump chain: any Spade beats any Club, which beats
     * any Heart, which beats any Diamond, when those suits were not led.
     */
    private boolean isActive(Suit cardSuit, Suit leadSuit) {
        return cardSuit.ordinal() >= leadSuit.ordinal();
    }

    /** Returns true if the play is legal given the player's hand and the current trick. */
    public boolean isValidPlay(com.acespade.model.Card card, List<com.acespade.model.Card> hand,
                               List<TrickCard> currentTrick) {
        // Any card is valid when leading a trick
        if (currentTrick.isEmpty()) return true;

        Suit leadSuit = currentTrick.get(0).getCard().getSuit();
        boolean hasLeadSuit = hand.stream().anyMatch(c -> c.getSuit() == leadSuit);

        // Must follow lead suit if possible
        if (hasLeadSuit && card.getSuit() != leadSuit) {
            return false;
        }
        return true;
    }
}
