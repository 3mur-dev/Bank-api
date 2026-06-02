package com.omar.bankapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginDTO(
        @NotBlank(message = "Username is required")
        @Size(max = 20, message = "Username must not exceed 20 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password must not exceed 72 characters")
        String password
) {
        public LoginDTO {
                username = username == null ? null : username.trim();
        }
}
