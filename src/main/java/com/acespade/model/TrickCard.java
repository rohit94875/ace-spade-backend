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
public class TrickCard implements Serializable {
    private static final long serialVersionUID = 1L;

    private String playerId;
    private String username;
    private Card card;
    /** 0-based position in trick — used for duplicate card tiebreaker (higher = later = wins). */
    private int playOrder;
}
