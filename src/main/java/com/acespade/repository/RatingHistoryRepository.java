package com.acespade.repository;

import com.acespade.domain.RatingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RatingHistoryRepository extends JpaRepository<RatingHistory, Long> {
    List<RatingHistory> findByUserIdAndSeasonIdOrderByCreatedAtDesc(Long userId, int seasonId);
}
