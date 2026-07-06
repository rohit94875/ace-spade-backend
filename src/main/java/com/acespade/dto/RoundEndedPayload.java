package com.acespade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundEndedPayload {
    private int round;
    /** playerId -> score earned this round */
    private Map<String, Integer> roundScores;
    /** playerId -> cumulative score after this round */
    private Map<String, Integer> cumulativeScores;
    /** playerId -> bid placed this round */
    private Map<String, Integer> bids;
    /** playerId -> tricks won this round */
    private Map<String, Integer> tricksWon;
    private boolean gameOver;
    /** Only present when gameOver=true */
    private String winnerUsername;
    private int winnerScore;
    /** True when game ended because a player left (FORFEIT_WIN policy). */
    private boolean forfeit;
    private String forfeitedUsername;
    /** playerId -> rating change; only on ranked game over */
    private Map<String, RatingDeltaDto> ratingUpdates;
}
