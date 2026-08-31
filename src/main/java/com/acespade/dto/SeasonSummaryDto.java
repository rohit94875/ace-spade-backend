package com.acespade.dto;

import com.acespade.model.enums.SeasonStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class SeasonSummaryDto {
    private int seasonId;
    private String name;
    private SeasonStatus status;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private boolean rewardsTracked;
}
