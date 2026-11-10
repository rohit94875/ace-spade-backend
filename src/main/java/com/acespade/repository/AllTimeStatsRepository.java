package com.acespade.repository;

import com.acespade.domain.GameRecordPlayer;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Read-only aggregates over existing game history tables. */
public interface AllTimeStatsRepository extends Repository<GameRecordPlayer, Long> {

    interface AllTimeRow {
        Long getUserId();
        String getUsername();
        Number getGames();
        Number getWins();
    }

    /** Ranked Classic + Ruthless games across all seasons, per registered player. */
    @Query(value = "SELECT grp.user_id AS userId, MAX(grp.username) AS username, "
            + "COUNT(*) AS games, "
            + "SUM(CASE WHEN gr.winner_username = grp.username THEN 1 ELSE 0 END) AS wins "
            + "FROM game_record_players grp "
            + "JOIN game_records gr ON gr.id = grp.game_record_id "
            + "WHERE grp.user_id IS NOT NULL AND gr.ranked = true "
            + "AND gr.game_mode IN ('CLASSIC', 'RUTHLESS_HIDDEN') "
            + "GROUP BY grp.user_id "
            + "HAVING COUNT(*) >= :minGames", nativeQuery = true)
    List<AllTimeRow> findAllTime(@Param("minGames") int minGames);
}
