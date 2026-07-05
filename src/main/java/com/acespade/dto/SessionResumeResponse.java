package com.acespade.dto;

import com.acespade.model.Card;
import com.acespade.model.TrickCard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResumeResponse {
    private boolean valid;
    private String playerId;
    private String username;
    private boolean host;
    private String roomCode;
    private RoomStateDto room;
    private List<Card> hand;
    private List<TrickCard> currentTrick;
    private List<ChatMessageDto> chatMessages;
    private Map<String, PlayerPresenceDto> presence;
    private String message;
}
