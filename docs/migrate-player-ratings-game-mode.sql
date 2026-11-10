-- Fix Ruthless (and multi-mode MMR) create/join failures.
-- Old unique key was (user_id, season_id); must be (user_id, season_id, game_mode).
-- Prefer restarting the app — RatingSchemaMigrator runs this automatically.
-- Manual fallback:

USE acespade;

-- Inspect current unique indexes:
-- SHOW INDEX FROM player_ratings WHERE Non_unique = 0;

-- Drop any unique index that is ONLY (user_id, season_id). Name varies (UK… / unique…).
-- Example:
-- ALTER TABLE player_ratings DROP INDEX UKxxxxxxxxxxxx;

ALTER TABLE player_ratings
  ADD UNIQUE KEY uk_user_season_mode (user_id, season_id, game_mode);
