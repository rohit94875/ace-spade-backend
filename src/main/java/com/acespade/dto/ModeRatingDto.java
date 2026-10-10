package com.acespade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeRatingDto {
    /** CLASSIC or RUTHLESS_HIDDEN */
    private String gameMode;
    private double mmr;
    private String tier;
    private boolean placementComplete;
    private int placementGames;
    private int placementRequired;
    private int gamesPlayed;
    /** 1-based ladder rank among placed players; null while still placing. */
    private Integer rank;
}
