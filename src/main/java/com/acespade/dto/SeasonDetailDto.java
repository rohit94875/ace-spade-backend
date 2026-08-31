package com.acespade.dto;

import com.acespade.model.enums.RewardSymbolType;
import com.acespade.model.enums.SeasonStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class SeasonDetailDto {
    private int seasonId;
    private String name;
    private SeasonStatus status;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private OffsetDateTime graceEndsAt;
    private boolean rewardsTracked;
    private List<SeasonRewardWinnerDto> awardWinners;
}
