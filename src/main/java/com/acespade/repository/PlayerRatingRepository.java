package com.acespade.repository;

import com.acespade.domain.PlayerRating;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerRatingRepository extends JpaRepository<PlayerRating, Long> {
    Optional<PlayerRating> findByUserIdAndSeasonId(Long userId, int seasonId);
    List<PlayerRating> findBySeasonIdOrderByRatingDesc(int seasonId, Pageable pageable);
    long countBySeasonIdAndPlacementGamesGreaterThanEqual(int seasonId, int minPlacement);
}
