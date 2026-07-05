package com.acespade.repository;

import com.acespade.model.GameRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameRecordRepository extends JpaRepository<GameRecord, Long> {
    List<GameRecord> findByRoomCodeOrderByPlayedAtDesc(String roomCode);
}
