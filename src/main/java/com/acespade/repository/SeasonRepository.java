package com.acespade.repository;

import com.acespade.domain.Season;
import com.acespade.model.enums.SeasonStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, Integer> {
    List<Season> findAllByOrderByIdDesc();
    Optional<Season> findFirstByStatusInOrderByIdDesc(List<SeasonStatus> statuses);
    Optional<Season> findFirstByStartsAt(Instant startsAt);
}
