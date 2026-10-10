package com.acespade.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Ensures player_ratings unique key is (user_id, season_id, game_mode).
 * Older DBs only had (user_id, season_id), which blocks Ruthless MMR rows
 * and surfaces as a confusing "Email or username already taken" on getRoom.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingSchemaMigrator {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        try {
            ensureGameModeColumn();
            dropStaleUniqueKeys();
            ensureCompositeUnique();
        } catch (Exception e) {
            log.warn("player_ratings schema migrate skipped/failed: {}", e.getMessage());
        }
    }

    private void ensureGameModeColumn() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'player_ratings' "
                        + "AND COLUMN_NAME = 'game_mode'",
                Integer.class);
        if (n != null && n == 0) {
            jdbcTemplate.execute(
                    "ALTER TABLE player_ratings ADD COLUMN game_mode VARCHAR(20) NOT NULL DEFAULT 'CLASSIC'");
            log.info("Added player_ratings.game_mode column");
        }
        jdbcTemplate.update(
                "UPDATE player_ratings SET game_mode = 'CLASSIC' WHERE game_mode IS NULL OR game_mode = ''");
    }

    private void dropStaleUniqueKeys() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols "
                        + "FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'player_ratings' "
                        + "AND NON_UNIQUE = 0 AND INDEX_NAME != 'PRIMARY' "
                        + "GROUP BY INDEX_NAME");
        for (Map<String, Object> row : indexes) {
            String name = String.valueOf(row.get("INDEX_NAME"));
            String cols = String.valueOf(row.get("cols")).toLowerCase().replace(" ", "");
            // Old key: user_id,season_id (no game_mode)
            if ("user_id,season_id".equals(cols)) {
                jdbcTemplate.execute("ALTER TABLE player_ratings DROP INDEX `" + name + "`");
                log.info("Dropped stale unique index {} on player_ratings({})", name, cols);
            }
        }
    }

    private void ensureCompositeUnique() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'player_ratings' "
                        + "AND INDEX_NAME = 'uk_user_season_mode'",
                Integer.class);
        if (n != null && n == 0) {
            // Also accept an existing unique that already has the right columns under another name
            List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                    "SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols "
                            + "FROM information_schema.STATISTICS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'player_ratings' "
                            + "AND NON_UNIQUE = 0 AND INDEX_NAME != 'PRIMARY' "
                            + "GROUP BY INDEX_NAME");
            boolean hasGood = indexes.stream().anyMatch(r -> {
                String cols = String.valueOf(r.get("cols")).toLowerCase().replace(" ", "");
                return "user_id,season_id,game_mode".equals(cols);
            });
            if (!hasGood) {
                jdbcTemplate.execute(
                        "ALTER TABLE player_ratings ADD UNIQUE KEY uk_user_season_mode (user_id, season_id, game_mode)");
                log.info("Added uk_user_season_mode on player_ratings");
            }
        }
    }
}
