package com.acespade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameEvent {

    public enum EventType {
        ROOM_UPDATED,
        ROUND_STARTED,
        BID_PHASE,
        BID_PLACED,
        PLAY_PHASE,
        CARD_PLAYED,
        TRICK_ENDED,
        ROUND_ENDED,
        GAME_ENDED,
        PLAYER_LEFT,
        BOT_TAKEOVER,
        GAME_PAUSED,
        GAME_RESUMED,
        ERROR
    }

    private EventType type;
    private Object payload;

    public static GameEvent of(EventType type, Object payload) {
        return new GameEvent(type, payload);
    }

    public static GameEvent error(String message) {
        return new GameEvent(EventType.ERROR, message);
    }
}
