package com.acespade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerSession implements Serializable {
    private static final long serialVersionUID = 1L;

    private String token;
    private String playerId;
    private String roomCode;
    private String username;
    private boolean host;
}
