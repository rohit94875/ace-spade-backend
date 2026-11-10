package com.acespade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpponentScoreDto {
    private String username;
    private int score;
    /** Null for bots and unranked games. */
    private Double ratingBefore;
    private Double ratingAfter;
    private Double ratingDelta;
}
