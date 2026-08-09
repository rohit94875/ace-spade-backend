package com.acespade.dto;

import lombok.Data;

import javax.validation.constraints.Size;
import java.util.List;

@Data
public class ChatRequest {
    @Size(max = 300)
    private String text;

    private List<String> mentions;
}
