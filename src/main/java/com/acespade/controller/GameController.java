package com.acespade.controller;

import com.acespade.dto.BidRequest;
import com.acespade.dto.PlayCardRequest;
import com.acespade.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Handles inbound STOMP messages from clients.
 * All methods require an authenticated principal (set by AuthChannelInterceptor).
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class GameController {

    private final RoomService roomService;

    /**
     * Host calls this to start the game.
     * Client sends to: /app/game/{roomCode}/start
     */
    @MessageMapping("/game/{roomCode}/start")
    public void startGame(@DestinationVariable String roomCode, Principal principal) {
        if (principal == null) return;
        log.debug("START received for room {} by {}", roomCode, principal.getName());
        roomService.startGame(roomCode, principal.getName());
    }

    /**
     * Player places a bid.
     * Client sends to: /app/game/{roomCode}/bid
     * Payload: { "amount": 3 }
     */
    @MessageMapping("/game/{roomCode}/bid")
    public void placeBid(@DestinationVariable String roomCode,
                         @Payload BidRequest request,
                         Principal principal) {
        if (principal == null) return;
        log.debug("BID {} received for room {} by {}", request.getAmount(), roomCode, principal.getName());
        roomService.placeBid(roomCode, principal.getName(), request.getAmount());
    }

    /**
     * Player plays a card.
     * Client sends to: /app/game/{roomCode}/play
     * Payload: { "suit": "SPADES", "rank": "ACE", "deckIndex": 0 }
     */
    @MessageMapping("/game/{roomCode}/play")
    public void playCard(@DestinationVariable String roomCode,
                         @Payload PlayCardRequest request,
                         Principal principal) {
        if (principal == null) return;
        log.debug("PLAY {}/{} received for room {} by {}",
                request.getSuit(), request.getRank(), roomCode, principal.getName());
        roomService.playCard(roomCode, principal.getName(),
                request.getSuit(), request.getRank(), request.getDeckIndex());
    }

    /**
     * Player voluntarily leaves the room.
     * Client sends to: /app/game/{roomCode}/leave
     */
    @MessageMapping("/game/{roomCode}/leave")
    public void leaveGame(@DestinationVariable String roomCode, Principal principal) {
        if (principal == null) return;
        log.debug("LEAVE received for room {} by {}", roomCode, principal.getName());
        roomService.playerLeft(roomCode, principal.getName());
    }

    @MessageMapping("/game/{roomCode}/pause")
    public void pauseGame(@DestinationVariable String roomCode, Principal principal) {
        if (principal == null) return;
        log.debug("PAUSE received for room {} by {}", roomCode, principal.getName());
        roomService.pauseGame(roomCode, principal.getName());
    }

    @MessageMapping("/game/{roomCode}/resume")
    public void resumeGame(@DestinationVariable String roomCode, Principal principal) {
        if (principal == null) return;
        log.debug("RESUME received for room {} by {}", roomCode, principal.getName());
        roomService.resumeGame(roomCode, principal.getName());
    }
}
