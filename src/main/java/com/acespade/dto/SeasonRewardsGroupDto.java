package com.acespade.dto;

import com.acespade.model.enums.SeasonStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeasonRewardsGroupDto {
    private int seasonId;
    private String seasonName;
    private SeasonStatus status;
    private boolean rewardsTracked;
    private List<SeasonRewardDto> rewards;
}
