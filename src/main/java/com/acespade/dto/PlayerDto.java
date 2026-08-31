package com.acespade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerDto {
    private String id;
    private String username;
    private Integer bid;
    private int tricksWon;
    private int cardCount;
    private boolean currentTurn;
    private boolean host;
    private boolean bot;
    private boolean connected;
    private Long graceExpiresAt;
    private long lastSeenAt;
    private String presenceStatus;
    private int autoPlayCount;
    private boolean ready;

    /** True when bid is placed but amount is hidden (Ruthless mode). */
    private boolean bidPlaced;

    /** Clan Battle team: 1 or 2. */
    private Integer teamId;

    /** Ranked tier badge after placement; null while placing or for bots/guests. */
    private String tier;
}
