package com.acespade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllTimeEntryDto {
    private int rank;
    private Long userId;
    private String username;
    private int games;
    private int wins;
    /** 0-100, one decimal place. */
    private double winPct;
}
