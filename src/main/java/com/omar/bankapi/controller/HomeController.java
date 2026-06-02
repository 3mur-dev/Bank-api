package com.omar.bankapi.controller;

import com.omar.bankapi.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/")
@Tag(name = "Home", description = "Public landing endpoint")
public class HomeController {

    @GetMapping
    public ApiResponse<?> home(HttpServletRequest request) {
        return ApiResponse.ok(Map.of("message", "Welcome to the Bank API. " +
                        "Available endpoints: " +
                        "/api/users, /api/accounts"),
                "Home",
                request
        );
    }
}
