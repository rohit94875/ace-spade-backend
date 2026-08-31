package com.acespade.repository;

import com.acespade.domain.SeasonReward;
import com.acespade.model.enums.RewardSymbolType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeasonRewardRepository extends JpaRepository<SeasonReward, Long> {
    List<SeasonReward> findBySeasonIdAndUserId(int seasonId, Long userId);
    List<SeasonReward> findBySeasonId(int seasonId);
    Optional<SeasonReward> findBySeasonIdAndSymbolType(int seasonId, RewardSymbolType symbolType);
}
