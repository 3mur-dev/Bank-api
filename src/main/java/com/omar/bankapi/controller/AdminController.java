package com.omar.bankapi.controller;

import com.omar.bankapi.dto.AdminStatsDTO;
import com.omar.bankapi.dto.ApiResponse;
import com.omar.bankapi.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@AllArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Administrative dashboard and statistics")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public ApiResponse<String> dashboard(HttpServletRequest request) {

        String message = "Welcome to the Admin Dashboard!" +
                "Endpoints:" + "/api/admin/dashboard   ---   /api/admin/stats";

        return ApiResponse.ok(
                message,
                "dashboard received successfully",
                request
        );
    }

    @GetMapping("/stats")
    public ApiResponse<AdminStatsDTO> stats(HttpServletRequest request) {

        return ApiResponse.ok(
                adminService.getStats(),
                "Stats received successfully",
                request
        );
    }
}
