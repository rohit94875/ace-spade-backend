# Ace Spade: Architecture

Consolidated view of both repositories. The same file is kept in both repos; update both when it changes.

| Repo | Role | Stack |
|---|---|---|
| `ace-spade-frontend` | Browser UI | React 18, TypeScript, Vite, Zustand, Framer Motion, axios, STOMP over SockJS |
| `ace-spade-backend` | API, realtime game server, ratings | Spring Boot 2.7, Java 8, Spring Security + JWT, STOMP WebSocket, JPA, Redis |

## System overview

```
 Browser (React SPA, served under /acespade/ in prod)
   |  REST  /api/**   (axios, Bearer JWT)
   |  STOMP /ws       (SockJS, X-Session-Token on CONNECT)
   v
 Reverse proxy (nginx in the external homelab compose stack; Vite dev proxy locally)
   v
 Spring Boot :8080
   |-- Controllers (REST + @MessageMapping) -> Services -> Engines
   |-- In-memory STOMP broker (/topic, /queue)   <- single node
   |-- In-memory timers (disconnect / turn)      <- lost on restart
   |-- Redis  : live GameState (game:{room}, 24h TTL) + player sessions
   `-- MySQL  : users, tokens, game records, ratings, seasons, rewards
```

Deployment: each repo has a Dockerfile. The frontend image contains only `/dist`, and the backend image is a JRE running the boot jar. The orchestrating `docker-compose.yml` (nginx, Redis, MySQL) lives outside both repos on the home server.

## Two identities

| Credential | Issued by | Used for | Stored client-side |
|---|---|---|---|
| JWT access + refresh | `/api/auth/*` | All REST calls except the public routes | localStorage `ace-spade-auth` |
| Room session token | create / join / rejoin / spectate | STOMP CONNECT (`X-Session-Token`); maps to a `playerId` in a room | localStorage `acespade_session` |

A user (JWT, MySQL `User`) can hold one room session (token, Redis `PlayerSession`) per game. The JWT proves *who you are*, and the session token proves *which seat you occupy*.

## Request flow

1. **Auth:** register (requires a registration code), login, refresh, logout through `AuthController`. The axios interceptor refreshes the access token 30s before expiry.
2. **Lobby:** `GET /api/rooms/public` lists lobby and in-progress rooms. `POST /api/rooms` and `/{code}/join|rejoin|spectate` return a session token plus a `playerId`.
3. **Connect:** the frontend opens SockJS `/ws` with the session token. `AuthChannelInterceptor` sets the STOMP principal to the `playerId` and calls `RoomService.onWebSocketConnect`, which pushes a snapshot.
4. **Play:** the client publishes to `/app/game/{room}/{action}`. `GameController` -> `RoomService` (per-room lock) -> engine -> save to Redis -> broadcast.
5. **Finish:** on game end, `RoomService` writes a `GameRecord`. `RatingService` applies Glicko-2 MMR for ranked-eligible modes, and season stats are updated.

## Realtime contract

Both repos must change together here, because the contract is duplicated by hand: backend `dto/GameEvent.EventType` and DTOs against frontend `src/types/game.ts` and `handleGameEvent` in `store/gameStore.ts`.

| Direction | Destination | Payload |
|---|---|---|
| server -> all in room | `/topic/game/{room}` | `GameEvent{type,payload}` |
| server -> one player | `/user/queue/hand` | `HandUpdate` |
| server -> one player | `/user/queue/snapshot` | `SessionResumeResponse` (full state) |
| server -> one player | `/user/queue/errors` | error `GameEvent` |
| client -> server | `/app/game/{room}/start`, `bid`, `play`, `ready`, `vote-bot`, `kick`, `team`, `poker`, `chat`, `leave`, `pause`, `resume` | per-action request DTO |

Event types: `ROOM_UPDATED`, `ROUND_STARTED`, `BID_PHASE`, `BID_PLACED`, `PLAY_PHASE`, `CARD_PLAYED`, `TRICK_ENDED`, `ROUND_ENDED`, `GAME_ENDED`, `PLAYER_LEFT`, `BOT_TAKEOVER`, `GAME_PAUSED`, `GAME_RESUMED`, `GAME_SNAPSHOT`, `PRESENCE_UPDATED`, `CHAT_MESSAGE`, `PLAYER_READY`, `BOT_VOTE_UPDATED`, `SPECTATOR_JOINED`, `PLAYER_KICKED`, `POKER_UPDATE`, `POKER_HAND_ENDED`, `ERROR`.

The client is a thin renderer. It holds no rule logic and applies server events to a Zustand store. Heartbeats run every 10s in both directions, and the client reconnects after 2s.

## Game model

- **Modes:** `CLASSIC`, `RUTHLESS_HIDDEN`, `CLAN_BATTLE` (teams), `POKER`. Only Classic and Ruthless are ranked (their own MMR, leaderboard and season rewards). Clan Battle and Poker only record history.
- **Phases:** `LOBBY -> BIDDING -> PLAYING -> TRICK_RESOLVE -> ROUND_END -> GAME_END`, with `POKER_HAND` for the poker flow. Casual games are capped at 5 rounds (see the frontend `constants/gameLength.ts`).
- **Rules code:** `GameEngine` and `TrickResolver` for trick modes, `PokerEngine` and `PokerHandEvaluator` for poker, and `BotService` for bots (bot name "BOT Vitality").
- **Concurrency:** every mutating action in `RoomService` runs under a per-room `ReentrantLock`, over a load-from-Redis, mutate, save, broadcast cycle.

## Disconnects and resilience

- Socket drop: 3s debounce, then a 120s grace window (presence `GRACE`), with a 45s per-turn timeout.
- Leaving or timing out is governed by `DisconnectPolicy`: `FORFEIT_WIN` ends the game, `BOT_TAKEOVER` seats a bot. Ranked leaves cost `2^n * ace.rating.forfeit-base-mmr` MMR.
- The frontend recovers on reload: `SessionBootstrap` -> `sessionRestore` -> `GET /api/sessions/me` (or authenticated rejoin) -> `applyResume` -> navigate to `/game`.
- Game state survives a backend restart (Redis), but the in-memory timers and the STOMP broker do not.

## Ratings and seasons

- Glicko-2 (`rating/Glicko2Calculator`) per `(user, season, game_mode)`. Tiers and reward symbols come from `TierUtil` and `RewardSymbolUtil`, with the frontend mirror in `constants/tiers.ts`.
- Season lifecycle `SCHEDULED -> ACTIVE -> GRACE -> COMPLETED`, advanced daily at 00:05 `Asia/Kolkata` (`SeasonScheduler`) and at boot (`SeasonBootstrap`). Rewards are computed by `SeasonRewardService`.
- Ops endpoints `POST /api/seasons/run-transitions` and `/{id}/finalize-rewards` are guarded only by the `X-Season-Ops-Token` header.

## Data stores

| Store | What | Notes |
|---|---|---|
| Redis | `GameState` JSON at `game:{roomCode}` (24h TTL); `PlayerSession` tokens | `GameStateRepository.findAll` uses `KEYS`, so it is O(N) and used for the public room list |
| MySQL | `User`, `RefreshToken`, `RegistrationSettings`, `GameRecord`, `GameRecordPlayer`, `PlayerRating`, `RatingHistory`, `Season`, `SeasonPlayerStats`, `SeasonReward` | Hibernate `ddl-auto=update`; no Flyway |

Schema changes beyond additive columns need manual or code migration: `RatingSchemaMigrator` runs at startup, and `docs/*.sql` hold manual fallbacks.

## Known constraints and risks

- **Single instance only:** the simple STOMP broker, the timers and the room locks are all in-process. Scaling out needs an external broker (e.g. a relay or Redis pub/sub) and distributed locks.
- **Insecure defaults:** `ace.jwt.secret` and `ace.season.ops-token` ship with placeholder values in `application.properties`, and the default MySQL password is `acespade123`. Override all three through environment variables outside local dev.
- **Open endpoints:** `/ws/**` is `permitAll` with `setAllowedOriginPatterns("*")`, and the season ops POSTs are `permitAll` at the filter level.
- **Contract drift:** event and DTO types are duplicated across the two repos with no shared schema or generated client.
- **Testing:** the backend has a single test class (`PokerHandEvaluatorTest`) and the frontend has none.
- **Runtime:** Java 8 and Spring Boot 2.7 are both past end of community support.
