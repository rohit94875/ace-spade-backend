package com.acespade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private String id;
    private String playerId;
    private String username;
    private String text;
    private List<String> mentions;
    private long sentAt;
}
