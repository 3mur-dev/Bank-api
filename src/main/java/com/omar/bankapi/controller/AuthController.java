package com.omar.bankapi.controller;

import com.omar.bankapi.dto.*;
import com.omar.bankapi.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration and login endpoints")
public class AuthController {

    private final AuthService authService;

   @PostMapping("/register")
   @ResponseStatus(HttpStatus.CREATED)
   public ApiResponse<UserDTO> createUser(@Valid @RequestBody CreateUserDTO dto, HttpServletRequest request) {

       return ApiResponse.ok(
               authService.register(dto),
               "user registered successfully",
               request
       );
   }

    @PostMapping("/login")
    public ApiResponse<AuthResponseDTO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {

        return ApiResponse.ok(
                authService.login(dto, resolveClientIp(request)),
                "user loged in successfully",
                request
        );
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
