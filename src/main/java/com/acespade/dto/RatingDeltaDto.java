package com.acespade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingDeltaDto {
    private Long userId;
    private String username;
    private double ratingBefore;
    private double ratingAfter;
    private double ratingDelta;
    private String tier;
    private boolean placementComplete;
    private int placementGames;
}
