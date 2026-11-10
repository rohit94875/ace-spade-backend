package com.acespade.model.enums;

public enum GamePhase {
    LOBBY,
    BIDDING,
    PLAYING,
    TRICK_RESOLVE,
    ROUND_END,
    GAME_END,
    /** Active poker hand (betting / dealing streets). */
    POKER_HAND
}
