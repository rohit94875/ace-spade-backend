package com.acespade.dto;

import com.acespade.model.enums.Rank;
import com.acespade.model.enums.Suit;
import lombok.Data;

@Data
public class PlayCardRequest {
    private Suit suit;
    private Rank rank;
    /** Client provides deckIndex so server can identify the correct copy when duplicates exist in hand. */
    private int deckIndex;
}
