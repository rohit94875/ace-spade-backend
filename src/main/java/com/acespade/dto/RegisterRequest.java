package com.acespade.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class RegisterRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank
    @Size(min = 3, max = 20)
    private String username;
}
