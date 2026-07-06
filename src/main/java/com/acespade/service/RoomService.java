package com.acespade.service;

import com.acespade.dto.*;
import com.acespade.domain.User;
import com.acespade.model.*;
import com.acespade.model.enums.DisconnectPolicy;
import com.acespade.model.enums.GamePhase;
import com.acespade.rating.TierUtil;
import com.acespade.repository.GameRecordRepository;
import com.acespade.repository.GameStateRepository;
import com.acespade.repository.SessionRepository;
import com.acespade.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Orchestrates room lifecycle and WebSocket broadcasts.
 * Uses per-room ReentrantLocks to prevent concurrent mutation of the same game state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private static final String ROOM_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ROOM_CODE_LENGTH = 6;
    private static final int MIN_PLAYERS = 2;

    private final GameStateRepository gameStateRepository;
    private final SessionRepository sessionRepository;
    private final GameRecordRepository gameRecordRepository;
    private final UserRepository userRepository;
    private final RatingService ratingService;
    private final GameEngine gameEngine;
    private final BotService botService;
    private final DisconnectScheduler disconnectScheduler;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, ReentrantLock> roomLocks = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    // -------------------------------------------------------------------------
    // REST operations
    // -------------------------------------------------------------------------

    public CreateRoomResponse createRoom(String username, boolean playWithBot,
                                         DisconnectPolicy disconnectPolicy, boolean ranked, Long userId) {
        if (ranked) {
            if (userId == null) {
                throw new IllegalArgumentException("Login required for ranked games");
            }
            if (playWithBot) {
                throw new IllegalArgumentException("Ranked games cannot include bots");
            }
        }

        username = resolveUsername(username, userId);
        String roomCode = generateUniqueRoomCode();
        String playerId = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();

        Player host = Player.builder()
                .id(playerId)
                .username(username)
                .userId(userId)
                .hand(new ArrayList<>())
                .bot(false)
                .connected(false)
                .lastSeenAt(System.currentTimeMillis())
                .build();

        GameState state = GameState.builder()
                .roomCode(roomCode)
                .hostPlayerId(playerId)
                .phase(GamePhase.LOBBY)
                .round(0)
                .players(new ArrayList<>(Collections.singletonList(host)))
                .scores(new HashMap<>())
                .playWithBot(playWithBot)
                .ranked(ranked)
                .disconnectPolicy(disconnectPolicy != null ? disconnectPolicy : DisconnectPolicy.FORFEIT_WIN)
                .build();
        state.getScores().put(playerId, 0);

        if (playWithBot) {
            Player bot = botService.createBotPlayer();
            state.getPlayers().add(bot);
            state.getScores().put(bot.getId(), 0);
        }

        gameStateRepository.save(state);

        PlayerSession session = PlayerSession.builder()
                .token(token)
                .playerId(playerId)
                .roomCode(roomCode)
                .username(username)
                .host(true)
                .userId(userId)
                .build();
        sessionRepository.save(session);

        log.info("Room {} created by {} (ranked={})", roomCode, username, ranked);
        return CreateRoomResponse.builder()
                .roomCode(roomCode)
                .playerId(playerId)
                .sessionToken(token)
                .username(username)
                .build();
    }

    public JoinRoomResponse joinRoom(String roomCode, String username, Long userId) {
        ReentrantLock lock = getRoomLock(roomCode);
        lock.lock();
        try {
            GameState state = gameStateRepository.findByRoomCode(roomCode)
                    .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomCode));

            if (state.getPhase() != GamePhase.LOBBY) {
                throw new IllegalStateException("Game has already started");
            }
            if (state.isRanked() && userId == null) {
                throw new IllegalArgumentException("Login required to join ranked games");
            }
            if (state.getPlayers().size() >= gameEngine.getMaxPlayers()) {
                throw new IllegalStateException("Room is full (max 8 players)");
            }
            if (username.toLowerCase().startsWith("bot vitality")) {
                throw new IllegalArgumentException("Username reserved for bots");
            }
            username = resolveUsername(username, userId);
            final String resolvedUsername = username;
            boolean nameTaken = state.getPlayers().stream()
                    .anyMatch(p -> p.getUsername().equalsIgnoreCase(resolvedUsername));
            if (nameTaken) {
                throw new IllegalArgumentException("Username already taken in this room");
            }

            String playerId = UUID.randomUUID().toString();
            String token = UUID.randomUUID().toString();

            Player player = Player.builder()
                    .id(playerId)
                    .username(resolvedUsername)
                    .userId(userId)
                    .hand(new ArrayList<>())
                    .bot(false)
                    .connected(false)
                    .lastSeenAt(System.currentTimeMillis())
                    .build();
            state.getPlayers().add(player);
            state.getScores().put(playerId, 0);
            gameStateRepository.save(state);

            PlayerSession session = PlayerSession.builder()
                    .token(token)
                    .playerId(playerId)
                    .roomCode(roomCode)
                    .username(resolvedUsername)
                    .host(false)
                    .userId(userId)
                    .build();
            sessionRepository.save(session);

            broadcastRoomUpdate(state);

            log.info("Player {} joined room {}", resolvedUsername, roomCode);
            return JoinRoomResponse.builder()
                    .roomCode(roomCode)
                    .playerId(playerId)
                    .sessionToken(token)
                    .username(resolvedUsername)
                    .build();
        } finally {
            lock.unlock();
        }
    }

    public RoomStateDto getRoomState(String roomCode) {
        GameState state = gameStateRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomCode));
        return toRoomStateDto(state, null);
    }

    public SessionResumeResponse resumeSession(String token) {
        return sessionRepository.findByToken(token)
                .map(session -> {
                    GameState state = gameStateRepository.findByRoomCode(session.getRoomCode()).orElse(null);
                    if (state == null) {
                        return SessionResumeResponse.builder()
                                .valid(false)
                                .message("Room no longer exists")
                                .build();
                    }
                    Player player = state.findPlayer(session.getPlayerId());
                    if (player == null || player.isBot()) {
                        return SessionResumeResponse.builder()
                                .valid(false)
                                .message("You are no longer in this room")
                                .build();
                    }
                    syncPresenceFromScheduler(state);
                    return buildSessionResumeResponse(state, session, player);
                })
                .orElse(SessionResumeResponse.builder()
                        .valid(false)
                        .message("Session expired — join again with room code")
                        .build());
    }

    public void onWebSocketConnect(String roomCode, String playerId) {
        disconnectScheduler.cancel(roomCode, playerId);
        ReentrantLock lock = getRoomLock(roomCode);
        lock.lock();
        try {
            GameState state = gameStateRepository.findByRoomCode(roomCode).orElse(null);
            if (state == null) {
                return;
            }
            Player player = state.findPlayer(playerId);
            if (player == null || player.isBot()) {
                return;
            }
            player.setConnected(true);
            player.setLastSeenAt(System.currentTimeMillis());
            player.setGraceExpiresAt(null);
            gameStateRepository.save(state);
            broadcastPresenceUpdate(state);
            sendGameSnapshotToPlayer(state, playerId);
            log.info("Player {} reconnected to room {}", player.getUsername(), roomCode);
        } finally {
            lock.unlock();
        }
    }

    public void sendChatMessage(String roomCode, String playerId, String text) {
        if (text == null) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.length() > 300) {
            sendError(playerId, "Message must be 1–300 characters");
            return;
        }

        ReentrantLock lock = getRoomLock(roomCode);
        lock.lock();
        try {
            GameState state = getStateOrThrow(roomCode);
            if (state.getPhase() == GamePhase.GAME_END) {
                sendError(playerId, "Game has ended");
                return;
            }
            Player player = state.findPlayer(playerId);
            if (player == null || player.isBot()) {
                sendError(playerId, "Cannot send chat");
                return;
            }

            ChatMessage msg = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .playerId(playerId)
                    .username(player.getUsername())
                    .text(trimmed)
                    .sentAt(System.currentTimeMillis())
                    .build();
            if (state.getChatMessages() == null) {
                state.setChatMessages(new ArrayList<>());
            }
            state.getChatMessages().add(msg);
            while (state.getChatMessages().size() > 100) {
                state.getChatMessages().remove(0);
            }
            gameStateRepository.save(state);

            ChatMessageDto dto = toChatDto(msg);
            broadcast(roomCode, GameEvent.of(GameEvent.EventType.CHAT_MESSAGE, dto));
        } finally {
            lock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // WebSocket game operations
    // -------------------------------------------------------------------------

    public void startGame(String roomCode, String playerId) {
        ReentrantLock lock = getRoomLock(roomCode);
        lock.lock();
        try {
            GameState state = getStateOrThrow(roomCode);

            if (!state.getHostPlayerId().equals(playerId)) {
                sendError(playerId, "Only the host can start the game");
                return;
            }
            if (state.getPhase() != GamePhase.LOBBY) {
                sendError(playerId, "Game already started");
                return;
            }
            if (state.getPlayers().size() < MIN_PLAYERS) {
                sendError(playerId, "Need at least " + MIN_PLAYERS + " players to start");
                return;
            }
            if (state.isRanked()) {
                boolean allLoggedIn = state.getPlayers().stream()
                        .filter(p -> !p.isBot())
                        .allMatch(p -> p.getUserId() != null);
                if (!allLoggedIn) {
                    sendError(playerId, "All players must be logged in to start a ranked game");
                    return;
                }
            }

            state.setRound(1);
            state = gameEngine.startRound(state);
            gameStateRepository.save(state);

            broadcastRoundStarted(state);
        } finally {
            lock.unlock();
        }
        processBotTurns(roomCode);
    }

    public void playerLeft(String roomCode, String playerId) {
        disconnectScheduler.cancel(roomCode, playerId);
        handlePlayerDeparture(roomCode, playerId, true);
    }

    public void onWebSocketDisconnect(String roomCode, String playerId) {
        ReentrantLock lock = getRoomLock(roomCode);
        lock.lock();
        try {
            GameState state = gameStateRepository.findByRoomCode(roomCode).orElse(null);
            if (state == null) {
                return;
            }
            Player player = state.findPlayer(playerId);
            if (player == null || player.isBot()) {
                return;
            }
            player.setConnected(false);
            player.setLastSeenAt(System.currentTimeMillis());

            if (isSoloBotGame(state) && isInProgressPhase(state)) {
                pauseGameInternal(state, playerId, true);
                player.setGraceExpiresAt(null);
                gameStateRepository.save(state);
                broadcastPresenceUpdate(state);
                return;
            }

            disconnectScheduler.scheduleDeparture(roomCode, playerId,
                    () -> handlePlayerDeparture(roomCode, playerId, false));
            player.setGraceExpiresAt(disconnectScheduler.getGraceExpiresAt(roomCode, playerId));
            gameStateRepository.save(state);
            broadcastPresenceUpdate(state);
        } finally {
            lock.unlock();
        }
    }

    public void onWebSocketReconnect(String roomCode, String playerId) {
        onWebSocketConnect(roomCode, playerId);
    }

    public void pauseGame(String roomCode, String playerId) {
        ReentrantLock lock = getRoomLock(roomCode);
        lock.lock();
        try {
            GameState state = getStateOrThrow(roomCode);
            pauseGameInternal(state, playerId, false);
        } finally {
            lock.unlock();
        }
    }

    public void resumeGame(String roomCode, String playerId) {
        boolean runBots = false;
        ReentrantLock lock = getRoomLock(roomCode);
        lock.lock();
        try {
            GameState state = getStateOrThrow(roomCode);
            if (!isSoloBotGame(state)) {
                sendError(playerId, "Pause is only available in 1v1 games vs BOT Vitality");
                return;
            }
            if (!state.isPaused()) {
                return;
            }
            Player player = state.findPlayer(playerId);
            if (player == null || player.isBot()) {
                sendError(playerId, "Only the human player can resume");
                return;
            }

            state.setPaused(false);
            state.setPausedByPlayerId(null);
            gameStateRepository.save(state);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("resumedByPlayerId", playerId);
            payload.put("resumedByUsername", player.getUsername());
            broadcast(roomCode, GameEvent.of(GameEvent.EventType.GAME_RESUMED, payload));
            broadcastPresenceUpdate(state);
            runBots = true;
            log.info("Game resumed in room {} by {}", roomCode, player.getUsername());
        } finally {
            lock.unlock();
        }
        if (runBots) {
            processBotTurns(roomCode);
        }
    }

    private void pauseGameInternal(GameState state, String playerId, boolean auto) {
        if (!isSoloBotGame(state)) {
            if (!auto) {
                sendError(playerId, "Pause is only available in 1v1 games vs BOT Vitality");
            }
            return;
        }
        if (!isPauseEligiblePhase(state)) {
            if (!auto) {
                sendError(playerId, "Cannot pause during lobby or after game ends");
            }
            return;
        }
        if (state.isPaused()) {
            return;
        }
        Player player = state.findPlayer(playerId);
        if (player == null || player.isBot()) {
            if (!auto) {
                sendError(playerId, "Only the human player can pause");
            }
            return;
        }

        state.setPaused(true);
        state.setPausedByPlayerId(playerId);
        player.setGraceExpiresAt(null);
        gameStateRepository.save(state);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pausedByPlayerId", playerId);
        payload.put("pausedByUsername", player.getUsername());
        payload.put("auto", auto);
        broadcast(state.getRoomCode(), GameEvent.of(GameEvent.EventType.GAME_PAUSED, payload));
        broadcastPresenceUpdate(state);
        log.info("Game paused in room {} by {} (auto={})", state.getRoomCode(), player.getUsername(), auto);
    }

    private boolean isSoloBotGame(GameState state) {
        if (!state.isPlayWithBot() || state.getPlayers().size() != 2) {
            return false;
        }
        long bots = state.getPlayers().stream().filter(Player::isBot).count();
        return bots == 1;
    }

    private boolean isPauseEligiblePhase(GameState state) {
        GamePhase phase = state.getPhase();
        return phase == GamePhase.BIDDING || phase == GamePhase.PLAYING;
    }

    private boolean isInProgressPhase(GameState state) {
        GamePhase phase = state.getPhase();
        return phase != GamePhase.LOBBY && phase != GamePhase.GAME_END;
    }

    private void ensureNotPaused(GameState state, String playerId) {
        if (state.isPaused()) {
            throw new IllegalStateException("Game is paused — tap Resume to continue");
        }
    }

    private void handlePlayerDeparture(String roomCode, String playerId, boolean explicitLeave) {
        boolean runBots = false;
        ReentrantLock lock = getRoomLock(roomCode);
        lock.lock();
        try {
            GameState state = gameStateRepository.findByRoomCode(roomCode).orElse(null);
            if (state == null) {
                return;
            }
            Player player = state.findPlayer(playerId);
            if (player == null || player.isBot()) {
                return;
            }

            sessionRepository.deleteByPlayerId(playerId);

            if (state.getPhase() == GamePhase.LOBBY) {
                removePlayerFromLobby(state, playerId);
                return;
            }

            if (state.getPhase() == GamePhase.GAME_END) {
                return;
            }

            if (state.getDisconnectPolicy() == DisconnectPolicy.BOT_TAKEOVER) {
                applyBotTakeover(state, player);
                gameStateRepository.save(state);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("playerId", playerId);
                payload.put("botPlayerId", player.getId());
                payload.put("botUsername", player.getUsername());
                payload.put("players", toPlayerDtoList(state, null));
                broadcast(roomCode, GameEvent.of(GameEvent.EventType.BOT_TAKEOVER, payload));
                runBots = true;
            } else {
                endGameByForfeit(state, player);
            }
            log.info("Player {} left room {} (explicit={})", player.getUsername(), roomCode, explicitLeave);
        } finally {
            lock.unlock();
        }
        if (runBots) {
            processBotTurns(roomCode);
        }
    }

    private void removePlayerFromLobby(GameState state, String playerId) {
        state.getPlayers().removeIf(p -> p.getId().equals(playerId));
        state.getScores().remove(playerId);
        if (state.getPlayers().isEmpty()) {
            gameStateRepository.delete(state.getRoomCode());
            return;
        }
        if (playerId.equals(state.getHostPlayerId()) && !state.getPlayers().isEmpty()) {
            Player newHost = state.getPlayers().stream()
                    .filter(p -> !p.isBot())
                    .findFirst()
                    .orElse(state.getPlayers().get(0));
            state.setHostPlayerId(newHost.getId());
        }
        gameStateRepository.save(state);
        broadcastRoomUpdate(state);
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("playerId", playerId);
        left.put("players", toPlayerDtoList(state, null));
        broadcast(state.getRoomCode(), GameEvent.of(GameEvent.EventType.PLAYER_LEFT, left));
    }

    private void applyBotTakeover(GameState state, Player player) {
        botService.convertToBot(player, state);
    }

    private void endGameByForfeit(GameState state, Player forfeiter) {
        List<Player> remaining = state.getPlayers().stream()
                .filter(p -> !p.getId().equals(forfeiter.getId()))
                .collect(Collectors.toList());

        Player winner = remaining.stream()
                .max(Comparator.comparingInt(p -> state.getScores().getOrDefault(p.getId(), 0)))
                .orElse(remaining.isEmpty() ? forfeiter : remaining.get(0));

        state.setPhase(GamePhase.GAME_END);
        gameStateRepository.save(state);

        Map<String, Integer> emptyRound = new LinkedHashMap<>();
        for (Player p : state.getPlayers()) {
            emptyRound.put(p.getId(), 0);
        }

        RoundEndedPayload payload = RoundEndedPayload.builder()
                .round(state.getRound())
                .roundScores(emptyRound)
                .cumulativeScores(new HashMap<>(state.getScores()))
                .bids(gameEngine.getBids(state))
                .tricksWon(gameEngine.getTricksWon(state))
                .gameOver(true)
                .winnerUsername(winner.getUsername())
                .winnerScore(state.getScores().getOrDefault(winner.getId(), 0))
                .forfeit(true)
                .forfeitedUsername(forfeiter.getUsername())
                .ratingUpdates(saveGameRecord(state))
                .build();

        broadcast(state.getRoomCode(), GameEvent.of(GameEvent.EventType.GAME_ENDED, payload));
    }

    private void processBotTurns(String roomCode) {
        while (true) {
            GameState state = gameStateRepository.findByRoomCode(roomCode).orElse(null);
            if (state == null) {
                break;
            }
            if (state.getPhase() == GamePhase.LOBBY || state.getPhase() == GamePhase.GAME_END) {
                break;
            }
            if (state.isPaused()) {
                break;
            }
            if (state.getPlayers().isEmpty()) {
                break;
            }
            Player current = state.getPlayers().get(state.getCurrentPlayerIndex());
            if (!current.isBot()) {
                break;
            }

            sleep(1200);

            if (state.getPhase() == GamePhase.BIDDING) {
                int bid = botService.decideBid(state, current);
                placeBid(roomCode, current.getId(), bid);
            } else if (state.getPhase() == GamePhase.PLAYING) {
                Card card = botService.decideCard(state, current);
                playCard(roomCode, current.getId(), card.getSuit(), card.getRank(), card.getDeckIndex());
            } else {
                break;
            }
        }
    }

    public void placeBid(String roomCode, String playerId, int amount) {
        ReentrantLock lock = getRoomLock(roomCode);
        lock.lock();
        try {
            GameState state = getStateOrThrow(roomCode);

            try {
                ensureNotPaused(state, playerId);
                state = gameEngine.placeBid(state, playerId, amount);
            } catch (Exception e) {
                sendError(playerId, e.getMessage());
                return;
            }

            gameStateRepository.save(state);

            Player bidder = state.findPlayer(playerId);
            String nextPlayerId = state.getPhase() == GamePhase.PLAYING
                    ? state.getPlayers().get(state.getLeadPlayerIndex()).getId()
                    : state.getPlayers().get(state.getCurrentPlayerIndex()).getId();

            Map<String, Object> bidPayload = new LinkedHashMap<>();
            bidPayload.put("playerId", playerId);
            bidPayload.put("username", bidder.getUsername());
            bidPayload.put("amount", amount);
            bidPayload.put("nextTurnPlayerId", nextPlayerId);
            bidPayload.put("phase", state.getPhase());

            broadcast(roomCode, GameEvent.of(GameEvent.EventType.BID_PLACED, bidPayload));

            if (state.getPhase() == GamePhase.PLAYING) {
                broadcastPlayPhase(state);
            }
        } finally {
            lock.unlock();
        }
        processBotTurns(roomCode);
    }

    public void playCard(String roomCode, String playerId,
                         com.acespade.model.enums.Suit suit,
                         com.acespade.model.enums.Rank rank,
                         int deckIndex) {
        ReentrantLock lock = getRoomLock(roomCode);
        lock.lock();
        try {
            GameState state = getStateOrThrow(roomCode);

            // Capture player info and trick state BEFORE the engine mutates anything
            Player player = state.findPlayer(playerId);
            if (player == null) {
                sendError(playerId, "Player not found");
                return;
            }
            int playOrder = state.getCurrentTrick().size();
            List<TrickCard> trickBefore = new ArrayList<>(state.getCurrentTrick());

            try {
                ensureNotPaused(state, playerId);
                state = gameEngine.playCard(state, playerId, suit, rank, deckIndex);
            } catch (Exception e) {
                sendError(playerId, e.getMessage());
                return;
            }

            gameStateRepository.save(state);

            // Reconstruct the played card from the known parameters — safe even after
            // the engine clears currentTrick on trick resolution.
            Card playedCard = Card.builder()
                    .suit(suit).rank(rank).deckIndex(deckIndex).playOrder(playOrder)
                    .build();
            TrickCard played = TrickCard.builder()
                    .playerId(playerId).username(player.getUsername())
                    .card(playedCard).playOrder(playOrder)
                    .build();

            Map<String, Object> cardPayload = new LinkedHashMap<>();
            cardPayload.put("playerId", playerId);
            cardPayload.put("username", player.getUsername());
            cardPayload.put("card", playedCard);
            broadcast(roomCode, GameEvent.of(GameEvent.EventType.CARD_PLAYED, cardPayload));

            // Full completed trick = cards already in trick before this play + this card
            List<TrickCard> completedTrick = new ArrayList<>(trickBefore);
            completedTrick.add(played);

            boolean trickResolved = state.getCurrentTrick().isEmpty()
                    || state.getPhase() == GamePhase.ROUND_END
                    || state.getPhase() == GamePhase.GAME_END;

            if (trickResolved) {
                // Give players 1.5 s to see the last card before resolving the trick.
                sleep(1500);
                broadcastTrickEnded(state, completedTrick);

                if (state.getPhase() == GamePhase.ROUND_END) {
                    broadcastRoundEnded(state, false);
                    // Give players 4 s to read the round summary before bidding starts.
                    sleep(4000);
                    state.setPhase(GamePhase.BIDDING);
                    state = gameEngine.startRound(state);
                    gameStateRepository.save(state);
                    broadcastRoundStarted(state);
                } else if (state.getPhase() == GamePhase.GAME_END) {
                    broadcastRoundEnded(state, true);
                } else {
                    // Let the trick-winner overlay linger for 1.5 s before the next trick.
                    sleep(1500);
                    broadcastPlayPhase(state);
                }
            } else {
                broadcastPlayPhase(state);
            }
        } finally {
            lock.unlock();
        }
        processBotTurns(roomCode);
    }
    // -------------------------------------------------------------------------

    private void broadcastRoomUpdate(GameState state) {
        broadcast(state.getRoomCode(),
                GameEvent.of(GameEvent.EventType.ROOM_UPDATED, toRoomStateDto(state, null)));
    }

    private void broadcastRoundStarted(GameState state) {
        // Broadcast public round info
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("round", state.getRound());
        payload.put("players", toPlayerDtoList(state, null));
        payload.put("currentTurnPlayerId", state.getPlayers().get(state.getCurrentPlayerIndex()).getId());
        broadcast(state.getRoomCode(), GameEvent.of(GameEvent.EventType.ROUND_STARTED, payload));

        // Send each human player their private hand (bots don't use WebSocket)
        for (Player player : state.getPlayers()) {
            if (player.isBot()) {
                continue;
            }
            HandUpdate handUpdate = HandUpdate.builder()
                    .round(state.getRound())
                    .hand(player.getHand())
                    .playerId(player.getId())
                    .build();
            messagingTemplate.convertAndSendToUser(player.getId(), "/queue/hand", handUpdate);
        }
    }

    private void broadcastPlayPhase(GameState state) {
        String currentPlayerId = state.getPlayers().get(state.getCurrentPlayerIndex()).getId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("currentTurnPlayerId", currentPlayerId);
        // currentTrick is intentionally omitted — the client builds it incrementally from
        // CARD_PLAYED events. Including it here caused duplicates when PLAY_PHASE arrived
        // before CARD_PLAYED due to async broker dispatch ordering.
        broadcast(state.getRoomCode(), GameEvent.of(GameEvent.EventType.PLAY_PHASE, payload));
    }

    private void broadcastTrickEnded(GameState state, List<TrickCard> completedTrick) {
        if (completedTrick.isEmpty()) return;

        TrickCard winner = new com.acespade.service.TrickResolver().resolveWinner(completedTrick);
        Map<String, Integer> trickCounts = new LinkedHashMap<>();
        for (Player p : state.getPlayers()) {
            trickCounts.put(p.getId(), p.getTricksWon());
        }

        TrickEndedPayload payload = TrickEndedPayload.builder()
                .winnerId(winner.getPlayerId())
                .winnerUsername(winner.getUsername())
                .trick(completedTrick)
                .trickCounts(trickCounts)
                .tricksPlayedInRound(state.getTricksPlayedInRound())
                .totalTricksInRound(state.getRound())
                .build();

        broadcast(state.getRoomCode(), GameEvent.of(GameEvent.EventType.TRICK_ENDED, payload));
    }

    private void broadcastRoundEnded(GameState state, boolean gameOver) {
        Map<String, Integer> roundScores = gameEngine.getRoundScores(state);
        Map<String, Integer> bids = gameEngine.getBids(state);
        Map<String, Integer> tricksWon = gameEngine.getTricksWon(state);

        // endRound() already incremented the round counter for the next round,
        // so subtract 1 to report the round that just finished.
        int completedRound = gameOver ? state.getRound() : state.getRound() - 1;

        Map<String, RatingDeltaDto> ratingUpdates = null;
        if (gameOver) {
            ratingUpdates = saveGameRecord(state);
        }

        RoundEndedPayload payload = RoundEndedPayload.builder()
                .round(completedRound)
                .roundScores(roundScores)
                .cumulativeScores(new HashMap<>(state.getScores()))
                .bids(bids)
                .tricksWon(tricksWon)
                .gameOver(gameOver)
                .winnerUsername(gameOver ? gameEngine.getWinnerUsername(state) : null)
                .winnerScore(gameOver ? gameEngine.getWinnerScore(state) : 0)
                .ratingUpdates(ratingUpdates)
                .build();

        broadcast(state.getRoomCode(), GameEvent.of(
                gameOver ? GameEvent.EventType.GAME_ENDED : GameEvent.EventType.ROUND_ENDED,
                payload));
    }

    private void broadcast(String roomCode, GameEvent event) {
        messagingTemplate.convertAndSend("/topic/game/" + roomCode, event);
    }

    private void sendError(String playerId, String message) {
        messagingTemplate.convertAndSendToUser(playerId, "/queue/errors",
                GameEvent.error(message));
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private Map<String, RatingDeltaDto> saveGameRecord(GameState state) {
        try {
            Map<String, Integer> usernameScores = new LinkedHashMap<>();
            for (Player p : state.getPlayers()) {
                usernameScores.put(p.getUsername(), state.getScores().getOrDefault(p.getId(), 0));
            }
            String scoresJson = objectMapper.writeValueAsString(usernameScores);

            GameRecord record = GameRecord.builder()
                    .roomCode(state.getRoomCode())
                    .playerCount(state.getPlayers().size())
                    .playerScoresJson(scoresJson)
                    .winnerUsername(gameEngine.getWinnerUsername(state))
                    .winnerScore(gameEngine.getWinnerScore(state))
                    .playedAt(LocalDateTime.now())
                    .ranked(state.isRanked())
                    .seasonId(TierUtil.CURRENT_SEASON_ID)
                    .build();

            record = gameRecordRepository.save(record);
            log.info("Game record saved for room {} (ranked={})", state.getRoomCode(), state.isRanked());

            if (state.isRanked() && !state.isPlayWithBot()) {
                return ratingService.processRankedGame(record, state.getPlayers(), state.getScores());
            }
            return null;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize game record for room {}", state.getRoomCode(), e);
            return null;
        }
    }

    private String resolveUsername(String requested, Long userId) {
        if (userId == null) {
            return requested.trim();
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        return user.getUsername();
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private GameState getStateOrThrow(String roomCode) {
        return gameStateRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomCode));
    }

    private ReentrantLock getRoomLock(String roomCode) {
        return roomLocks.computeIfAbsent(roomCode, k -> new ReentrantLock());
    }

    private String generateUniqueRoomCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(ROOM_CODE_LENGTH);
            for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
                sb.append(ROOM_CODE_CHARS.charAt(random.nextInt(ROOM_CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (gameStateRepository.exists(code));
        return code;
    }

    private RoomStateDto toRoomStateDto(GameState state, String currentPlayerId) {
        syncPresenceFromScheduler(state);
        String currentTurnId = state.getPlayers().isEmpty() ? null
                : state.getPlayers().get(state.getCurrentPlayerIndex() % state.getPlayers().size()).getId();
        return RoomStateDto.builder()
                .roomCode(state.getRoomCode())
                .phase(state.getPhase())
                .round(state.getRound())
                .players(toPlayerDtoList(state, currentTurnId))
                .scores(state.getScores())
                .currentTurnPlayerId(currentTurnId)
                .hostPlayerId(state.getHostPlayerId())
                .playWithBot(state.isPlayWithBot())
                .ranked(state.isRanked())
                .disconnectPolicy(state.getDisconnectPolicy())
                .paused(state.isPaused())
                .pausedByPlayerId(state.getPausedByPlayerId())
                .chatMessages(toChatDtoList(state.getChatMessages()))
                .presence(toPresenceMap(state))
                .build();
    }

    private SessionResumeResponse buildSessionResumeResponse(GameState state, PlayerSession session, Player player) {
        RoomStateDto room = toRoomStateDto(state, session.getPlayerId());
        List<Card> hand = player.getHand() == null ? Collections.emptyList() : new ArrayList<>(player.getHand());
        List<TrickCard> trick = state.getCurrentTrick() == null
                ? Collections.emptyList() : new ArrayList<>(state.getCurrentTrick());
        return SessionResumeResponse.builder()
                .valid(true)
                .playerId(session.getPlayerId())
                .username(session.getUsername())
                .host(session.isHost())
                .roomCode(session.getRoomCode())
                .room(room)
                .hand(hand)
                .currentTrick(trick)
                .chatMessages(toChatDtoList(state.getChatMessages()))
                .presence(toPresenceMap(state))
                .build();
    }

    private void sendGameSnapshotToPlayer(GameState state, String playerId) {
        Player player = state.findPlayer(playerId);
        if (player == null) {
            return;
        }
        PlayerSession session = PlayerSession.builder()
                .playerId(playerId)
                .roomCode(state.getRoomCode())
                .username(player.getUsername())
                .host(playerId.equals(state.getHostPlayerId()))
                .build();
        SessionResumeResponse snapshot = buildSessionResumeResponse(state, session, player);
        messagingTemplate.convertAndSendToUser(playerId, "/queue/snapshot", snapshot);
    }

    private void broadcastPresenceUpdate(GameState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("presence", toPresenceMap(state));
        payload.put("graceSeconds", disconnectScheduler.getGraceSeconds());
        broadcast(state.getRoomCode(), GameEvent.of(GameEvent.EventType.PRESENCE_UPDATED, payload));
    }

    private void syncPresenceFromScheduler(GameState state) {
        if (state.getPlayers() == null) {
            return;
        }
        for (Player p : state.getPlayers()) {
            if (p.isBot()) {
                p.setConnected(true);
                continue;
            }
            if (!p.isConnected()) {
                Long grace = disconnectScheduler.getGraceExpiresAt(state.getRoomCode(), p.getId());
                p.setGraceExpiresAt(grace);
            }
        }
    }

    private Map<String, PlayerPresenceDto> toPresenceMap(GameState state) {
        Map<String, PlayerPresenceDto> map = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (Player p : state.getPlayers()) {
            if (p.isBot()) {
                map.put(p.getId(), PlayerPresenceDto.builder()
                        .playerId(p.getId())
                        .username(p.getUsername())
                        .connected(true)
                        .bot(true)
                        .lastSeenAt(p.getLastSeenAt())
                        .status("ONLINE")
                        .build());
                continue;
            }
            String status = resolvePresenceStatus(state, p, now);
            map.put(p.getId(), PlayerPresenceDto.builder()
                    .playerId(p.getId())
                    .username(p.getUsername())
                    .connected(p.isConnected())
                    .bot(false)
                    .graceExpiresAt(p.getGraceExpiresAt())
                    .lastSeenAt(p.getLastSeenAt())
                    .status(status)
                    .build());
        }
        return map;
    }

    private String resolvePresenceStatus(GameState state, Player p, long now) {
        if (state.isPaused() && p.getId().equals(state.getPausedByPlayerId())) {
            return "PAUSED";
        }
        if (p.isConnected()) {
            return "ONLINE";
        }
        if (p.getGraceExpiresAt() != null && p.getGraceExpiresAt() > now) {
            return "GRACE";
        }
        return "DISCONNECTED";
    }

    private List<ChatMessageDto> toChatDtoList(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        return messages.stream().map(this::toChatDto).collect(Collectors.toList());
    }

    private ChatMessageDto toChatDto(ChatMessage msg) {
        return ChatMessageDto.builder()
                .id(msg.getId())
                .playerId(msg.getPlayerId())
                .username(msg.getUsername())
                .text(msg.getText())
                .sentAt(msg.getSentAt())
                .build();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<PlayerDto> toPlayerDtoList(GameState state, String currentTurnId) {
        long now = System.currentTimeMillis();
        return state.getPlayers().stream().map(p -> PlayerDto.builder()
                .id(p.getId())
                .username(p.getUsername())
                .bid(p.getBid())
                .tricksWon(p.getTricksWon())
                .cardCount(p.getHand() == null ? 0 : p.getHand().size())
                .currentTurn(p.getId().equals(currentTurnId))
                .host(p.getId().equals(state.getHostPlayerId()))
                .bot(p.isBot())
                .connected(p.isBot() || p.isConnected())
                .graceExpiresAt(p.getGraceExpiresAt())
                .lastSeenAt(p.getLastSeenAt())
                .presenceStatus(p.isBot() ? "ONLINE" : resolvePresenceStatus(state, p, now))
                .build()
        ).collect(Collectors.toList());
    }
}
