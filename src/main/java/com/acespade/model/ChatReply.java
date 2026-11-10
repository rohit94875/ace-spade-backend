package com.acespade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Server-built snapshot of the message a chat message replies to. Built from the stored
 * original so clients cannot forge a quote. Survives the original being trimmed from the room.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatReply implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Id of the original message, used by clients to jump to it while it is still loaded. */
    private String id;
    private String username;
    /** Original text, truncated. */
    private String text;
}
