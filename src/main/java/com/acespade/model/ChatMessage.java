package com.acespade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String playerId;
    private String username;
    private String text;
    private List<String> mentions;
    /** Quote of the message this one replies to, or null. */
    private ChatReply replyTo;
    /** Epoch millis. */
    private long sentAt;
}
