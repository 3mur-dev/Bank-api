package com.omar.bankapi.controller;

import com.omar.bankapi.dto.*;
import com.omar.bankapi.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "Users", description = "User profile management")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<UserDTO>> getAllUsers(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                userService.getAllUsers(pageable, includeDeleted),
                "Users retrieved successfully",
                request
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDTO> getUser(
            @Positive @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                userService.getUserById(id, includeDeleted),
                "User retrieved successfully",
                request
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<UserDTO> updateUser(
            @Positive @PathVariable Long id,
            @Valid @RequestBody UpdateUserDTO dto,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                userService.updateUser(id, dto),
                "User updated successfully",
                request
        );
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserDTO> closeUser(
            @Positive @PathVariable Long id,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(
                userService.closeUser(id),
                "User closed successfully",
                request
        );
    }
}
