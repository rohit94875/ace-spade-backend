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
public class Spectator implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String username;
    private Long userId;
    @Builder.Default
    private boolean connected = false;
    private long lastSeenAt;
}
