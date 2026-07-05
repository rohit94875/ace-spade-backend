package com.acespade.model.enums;

/**
 * Suit ordering by ordinal: DIAMONDS(0) < HEARTS(1) < CLUBS(2) < SPADES(3).
 * Higher ordinal = higher trump rank.
 * Spades is the supreme trump; Clubs beats Hearts and Diamonds; etc.
 */
public enum Suit {
    DIAMONDS,
    HEARTS,
    CLUBS,
    SPADES
}
