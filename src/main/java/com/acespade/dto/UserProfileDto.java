package com.acespade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private Long id;
    private String email;
    private String username;
    private Double mmr;
    private String tier;
    private boolean placementComplete;
    private int placementGames;
    private int placementRequired;
    private int gamesPlayed;
    private int seasonId;
}
