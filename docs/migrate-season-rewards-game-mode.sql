-- One-time migration for multi-mode season rewards (october-release).
-- Safe to run if Hibernate ddl-auto did not fully update the unique key.

USE acespade;

-- Backfill mode for any existing awards (all were Classic-only before this change)
UPDATE season_rewards
SET game_mode = 'CLASSIC'
WHERE game_mode IS NULL OR game_mode = '';

-- Drop old unique key if present (name may vary; check SHOW INDEX FROM season_rewards)
-- ALTER TABLE season_rewards DROP INDEX UKxxxxxxxx;

-- Ensure unique includes game_mode so Classic and Ruthless can both award TOP_MMR etc.
-- ALTER TABLE season_rewards
--   ADD UNIQUE KEY uk_season_user_mode_symbol (season_id, user_id, game_mode, symbol_type);
