package com.acespade.security;

import com.acespade.model.PlayerSession;
import com.acespade.repository.SessionRepository;
import com.acespade.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Applies disconnect policy after grace period when a WebSocket session drops.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketDisconnectListener {

    private final RoomService roomService;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (accessor.getSessionAttributes() == null) {
            return;
        }
        String playerId = (String) accessor.getSessionAttributes().get("playerId");
        String roomCode = (String) accessor.getSessionAttributes().get("roomCode");
        if (playerId != null && roomCode != null) {
            log.debug("WebSocket disconnected for player {} in room {}", playerId, roomCode);
            roomService.onWebSocketDisconnect(roomCode, playerId);
        }
    }
}
