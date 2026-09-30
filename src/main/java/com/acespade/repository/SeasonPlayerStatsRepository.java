package com.acespade.repository;

import com.acespade.domain.SeasonPlayerStats;
import com.acespade.model.enums.GameMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeasonPlayerStatsRepository extends JpaRepository<SeasonPlayerStats, Long> {
    Optional<SeasonPlayerStats> findBySeasonIdAndUserIdAndGameMode(int seasonId, Long userId, GameMode gameMode);
    List<SeasonPlayerStats> findBySeasonIdAndGameMode(int seasonId, GameMode gameMode);
}
