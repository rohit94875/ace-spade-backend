package com.acespade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private Long id;
    private String email;
    private String username;
    /** Classic-mode MMR (kept for older clients). */
    private Double mmr;
    private String tier;
    private boolean placementComplete;
    private int placementGames;
    private int placementRequired;
    private int gamesPlayed;
    private int seasonId;
    /** Ranked games forfeited by leaving (this season, Classic). */
    private int leaveCount;
    /** MMR loss if they leave again: 2^leaveCount * base. */
    private double nextLeavePenaltyMmr;
    /** Classic + Ruthless ratings for the current season. */
    private List<ModeRatingDto> modeRatings;
}
