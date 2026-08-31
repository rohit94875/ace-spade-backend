package com.acespade.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

@Data
public class RoomSettingsRequest {
    @Min(8)
    @Max(13)
    private Integer maxRounds;

    @Size(min = 2, max = 24)
    private String team1Name;

    @Size(min = 2, max = 24)
    private String team2Name;
}
