package com.omar.bankapi.mapper;

import com.omar.bankapi.dto.UserDTO;
import com.omar.bankapi.model.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserDTO toDTO(User user) {

        if (user == null) {
            return null;
        }

        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().getName() : null,
                user.isDeleted(),
                user.getDeletedAt()
        );
    }
}
