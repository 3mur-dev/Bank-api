package com.omar.bankapi.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/")
public class HomeController {

    @GetMapping
    public ResponseEntity<?> home() {
        return ResponseEntity.ok(Map.of("message", "Welcome to the Bank API. " +
                "Available endpoints: " +
                "/api/users, /api/accounts"));
    }
}
