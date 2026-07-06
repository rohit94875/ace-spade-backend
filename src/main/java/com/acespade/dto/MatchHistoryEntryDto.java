package com.acespade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchHistoryEntryDto {
    private Long gameRecordId;
    private String roomCode;
    private int score;
    private boolean won;
    private Double ratingBefore;
    private Double ratingAfter;
    private Double ratingDelta;
    private Instant playedAt;
}
