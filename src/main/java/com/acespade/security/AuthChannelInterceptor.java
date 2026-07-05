package com.acespade.security;

import com.acespade.model.PlayerSession;
import com.acespade.repository.SessionRepository;
import com.acespade.service.DisconnectScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;

/**
 * Intercepts the STOMP CONNECT frame to authenticate the player via their session token.
 * Sets the WebSocket principal to the player's ID so that private messages
 * via convertAndSendToUser() are routed correctly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final SessionRepository sessionRepository;
    private final DisconnectScheduler disconnectScheduler;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("X-Session-Token");
            if (token != null) {
                Optional<PlayerSession> session = sessionRepository.findByToken(token);
                if (session.isPresent()) {
                    final String playerId = session.get().getPlayerId();
                    final String roomCode = session.get().getRoomCode();
                    accessor.getSessionAttributes().put("playerId", playerId);
                    accessor.getSessionAttributes().put("roomCode", roomCode);
                    accessor.setUser(new Principal() {
                        @Override
                        public String getName() {
                            return playerId;
                        }
                    });
                    disconnectScheduler.cancel(roomCode, playerId);
                    log.debug("WebSocket CONNECT authenticated for player {}", playerId);
                } else {
                    log.warn("WebSocket CONNECT with invalid token");
                }
            }
        }
        return message;
    }
}
