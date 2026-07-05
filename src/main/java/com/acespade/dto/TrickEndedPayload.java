package com.acespade.dto;

import com.acespade.model.TrickCard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrickEndedPayload {
    private String winnerId;
    private String winnerUsername;
    private List<TrickCard> trick;
    /** playerId -> tricksWon count for current round */
    private Map<String, Integer> trickCounts;
    private int tricksPlayedInRound;
    private int totalTricksInRound;
}
