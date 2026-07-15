package com.acespade.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class RefreshRequest {
    @NotBlank
    private String refreshToken;
}
