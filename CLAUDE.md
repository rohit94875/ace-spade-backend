# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Backend for **Ace Spade**, a multiplayer card game (Spades-style bidding/trick-taking, Clan Battle, Poker) with MMR ranking and seasons. Spring Boot 2.7 on **Java 8** (`sourceCompatibility 1.8`, Lombok). The React client lives in `ace-spade-frontend`; see `ARCHITECTURE.md` for the full cross-repo picture.

## Commands

```bash
./gradlew bootRun                                   # :8080, needs MySQL :3306 and Redis :6379
./gradlew bootJar -x test                           # what the Dockerfile builds
./gradlew test                                      # JUnit 5
./gradlew test --tests com.acespade.service.PokerHandEvaluatorTest
./gradlew test --tests '*PokerHandEvaluatorTest.someMethod'
```

- Only one test class exists (`PokerHandEvaluatorTest`); there is no lint or format task.
- Needs a JDK that can compile Java 8 targets. The Docker build uses `gradle:7.6.4-jdk8`.
- Config comes from env vars with dev defaults in `application.properties`: `MYSQL_URL/USER/PASSWORD`, `REDIS_HOST/PORT`, `JWT_SECRET`. The defaults for `ace.jwt.secret` and `ace.season.ops-token` are insecure placeholders and must be overridden in any real deployment. The MySQL URL uses `createDatabaseIfNotExist=true`.

## Architecture

**Layering:** `controller` (REST + STOMP `@MessageMapping`) -> `service` -> `repository` (JPA for MySQL, a hand-written Redis repo for live games). `domain/` holds JPA entities, `model/` holds the in-memory/Redis game objects, `dto/` the wire types.

**Live game state is in Redis, not MySQL.** `GameStateRepository` serializes the whole `GameState` as JSON under `game:{roomCode}` with a 24h TTL. `PlayerSession` tokens are in Redis too (`SessionRepository`). MySQL (Hibernate `ddl-auto=update`) holds only durable data: users, refresh tokens, ratings/history, game records, seasons, rewards. Finished games are written to MySQL as `GameRecord`s.

**`RoomService` (~2200 lines) is the orchestrator.** Every state-changing action (join, start, bid, play, poker, leave, kick, chat) runs under a **per-room `ReentrantLock`** (`roomLocks`), loads `GameState` from Redis, delegates rules to an engine, saves it back, then broadcasts. Preserve this load -> mutate -> save -> broadcast pattern and the locking when adding actions. Rules are in the engines:
- `GameEngine` (bidding/tricks/scoring for Classic, Ruthless, Clan), `TrickResolver`
- `PokerEngine` + `PokerHandEvaluator`
- `BotService` (BOT Vitality; also takes over a leaver's seat under `DisconnectPolicy.BOT_TAKEOVER`)

**Realtime protocol** (`WebSocketConfig`): SockJS endpoint `/ws`; simple in-memory broker on `/topic` and `/queue`; client sends to `/app/...`; 10s heartbeats. Public events go to `/topic/game/{room}` as `GameEvent{type,payload}` (enum in `dto/GameEvent`); per-player data goes via `convertAndSendToUser` to `/queue/hand`, `/queue/snapshot` and `/queue/errors`. The broker is single-node in-memory, so this app cannot scale horizontally without replacing it.

**Two auth systems** (`SecurityConfig`): stateless JWT (`JwtAuthFilter`) for REST, and a room **session token** for STOMP. `AuthChannelInterceptor` reads `X-Session-Token` on CONNECT, sets the Principal to the `playerId`, and calls `RoomService.onWebSocketConnect`. Note that `/ws/**` and several `GET /api/seasons|rankings|rooms` routes are `permitAll`. New endpoints are authenticated by default (`anyRequest().authenticated()`); admin routes need `ROLE_ADMIN`. The season ops endpoints (`/api/seasons/run-transitions`, `/{id}/finalize-rewards`) are `permitAll` in the filter chain and are guarded only by the `X-Season-Ops-Token` header check in the controller.

**Disconnect handling** (`DisconnectScheduler`, in-memory `ConcurrentHashMap` timers): 3s debounce after a socket drops, then a 120s reconnect grace, plus a 45s turn timeout. All of these are lost on restart. Leaving mid-game follows `DisconnectPolicy` (`FORFEIT_WIN` or `BOT_TAKEOVER`); ranked forfeits cost `2^n * ace.rating.forfeit-base-mmr` MMR.

**Ratings and seasons.** `RatingService` + `rating/Glicko2Calculator` compute MMR for **ranked-eligible modes only** (`GameMode.rankedModes()`: `CLASSIC`, `RUTHLESS_HIDDEN`); Clan Battle and Poker are recorded without MMR. Ratings are keyed `(user_id, season_id, game_mode)`. `SeasonService`/`SeasonRewardService` drive the season lifecycle `SCHEDULED -> ACTIVE -> GRACE -> COMPLETED`, advanced by `SeasonScheduler` (daily 00:05 `Asia/Kolkata`) and on boot via `SeasonBootstrap`.

**Schema migrations are code, not Flyway.** `RatingSchemaMigrator` fixes the `player_ratings` unique key at startup (`ApplicationReadyEvent`), and `docs/*.sql` are manual fallbacks. When changing a unique key or adding a mode-scoped column, remember Hibernate `update` will not drop old indexes.

`AdminBootstrap` promotes `ace.admin.bootstrap-email` to admin on startup. The registration-code feature (`RegistrationCodeService`, admin API) gates sign-ups.
