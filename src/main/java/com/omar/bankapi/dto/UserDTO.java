package com.omar.bankapi.dto;

import java.time.Instant;

public record UserDTO(
        Long id,
        String username,
        String email,
        String role,
        boolean deleted,
        Instant deletedAt
) {
}
