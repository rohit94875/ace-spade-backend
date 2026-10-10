package com.acespade.dto;

import lombok.Data;

@Data
public class PokerActionRequest {
    /** FOLD, CHECK, CALL, BET, RAISE */
    private String action;
}
