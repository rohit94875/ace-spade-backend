package com.acespade.dto;

import com.acespade.model.enums.RewardSymbolType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SeasonRewardWinnerDto {
    private RewardSymbolType symbolType;
    private Long userId;
    private String username;
    private Double statValue;
}
