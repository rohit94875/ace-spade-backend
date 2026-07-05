package com.acespade.dto;

import com.acespade.model.Card;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandUpdate {
    private int round;
    private List<Card> hand;
    private String playerId;
}
