package com.acespade.dto;

import com.acespade.model.enums.RewardSymbolType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SeasonRewardDto {
    private RewardSymbolType symbolType;
    private Double statValue;
}
