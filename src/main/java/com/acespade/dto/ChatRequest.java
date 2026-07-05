package com.acespade.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class ChatRequest {
    @NotBlank
    @Size(max = 300)
    private String text;
}
