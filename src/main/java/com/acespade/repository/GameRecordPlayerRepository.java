package com.acespade.repository;

import com.acespade.domain.GameRecordPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameRecordPlayerRepository extends JpaRepository<GameRecordPlayer, Long> {
    List<GameRecordPlayer> findByUserIdOrderByIdDesc(Long userId);
    List<GameRecordPlayer> findByGameRecordId(Long gameRecordId);
}
