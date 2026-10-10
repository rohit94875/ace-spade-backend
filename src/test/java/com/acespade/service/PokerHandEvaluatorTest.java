package com.acespade.service;

import com.acespade.model.Card;
import com.acespade.model.enums.Rank;
import com.acespade.model.enums.Suit;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokerHandEvaluatorTest {

    private final PokerHandEvaluator eval = new PokerHandEvaluator();

    private Card c(Suit s, Rank r) {
        return Card.builder().suit(s).rank(r).deckIndex(0).playOrder(0).build();
    }

    @Test
    void royalFlushBeatsFullHouse() {
        List<Card> hole = Arrays.asList(c(Suit.SPADES, Rank.ACE), c(Suit.SPADES, Rank.KING));
        List<Card> board = Arrays.asList(
                c(Suit.SPADES, Rank.QUEEN),
                c(Suit.SPADES, Rank.JACK),
                c(Suit.SPADES, Rank.TEN),
                c(Suit.HEARTS, Rank.TWO),
                c(Suit.CLUBS, Rank.THREE));
        long sf = eval.evaluate(hole, board);

        List<Card> hole2 = Arrays.asList(c(Suit.HEARTS, Rank.ACE), c(Suit.DIAMONDS, Rank.ACE));
        List<Card> board2 = Arrays.asList(
                c(Suit.CLUBS, Rank.ACE),
                c(Suit.HEARTS, Rank.KING),
                c(Suit.DIAMONDS, Rank.KING),
                c(Suit.SPADES, Rank.TWO),
                c(Suit.CLUBS, Rank.THREE));
        long fh = eval.evaluate(hole2, board2);

        assertTrue(sf > fh);
        assertEquals(PokerHandEvaluator.STRAIGHT_FLUSH, eval.category(sf));
        assertEquals(PokerHandEvaluator.FULL_HOUSE, eval.category(fh));
    }

    @Test
    void wheelStraightDetected() {
        List<Card> hole = Arrays.asList(c(Suit.HEARTS, Rank.ACE), c(Suit.CLUBS, Rank.TWO));
        List<Card> board = Arrays.asList(
                c(Suit.DIAMONDS, Rank.THREE),
                c(Suit.SPADES, Rank.FOUR),
                c(Suit.HEARTS, Rank.FIVE),
                c(Suit.CLUBS, Rank.KING),
                c(Suit.DIAMONDS, Rank.QUEEN));
        long score = eval.evaluate(hole, board);
        assertEquals(PokerHandEvaluator.STRAIGHT, eval.category(score));
    }

    @Test
    void pairBeatsHighCard() {
        long pair = eval.evaluate(
                Arrays.asList(c(Suit.HEARTS, Rank.NINE), c(Suit.CLUBS, Rank.NINE)),
                Arrays.asList(
                        c(Suit.DIAMONDS, Rank.TWO),
                        c(Suit.SPADES, Rank.THREE),
                        c(Suit.HEARTS, Rank.FOUR),
                        c(Suit.CLUBS, Rank.SIX),
                        c(Suit.DIAMONDS, Rank.SEVEN)));
        long high = eval.evaluate(
                Arrays.asList(c(Suit.HEARTS, Rank.ACE), c(Suit.CLUBS, Rank.KING)),
                Arrays.asList(
                        c(Suit.DIAMONDS, Rank.TWO),
                        c(Suit.SPADES, Rank.THREE),
                        c(Suit.HEARTS, Rank.FOUR),
                        c(Suit.CLUBS, Rank.SIX),
                        c(Suit.DIAMONDS, Rank.SEVEN)));
        assertTrue(pair > high);
        assertEquals(PokerHandEvaluator.ONE_PAIR, eval.category(pair));
        assertEquals(PokerHandEvaluator.HIGH_CARD, eval.category(high));
    }
}
