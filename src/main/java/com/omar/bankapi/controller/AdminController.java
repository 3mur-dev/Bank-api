package com.omar.bankapi.controller;

import com.omar.bankapi.dto.AdminStatsDTO;
import com.omar.bankapi.service.AdminService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@AllArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public String dashboard() {
        return "Welcome to the Admin Dashboard!" +
                "Endpoints:" + "/api/admin/dashboard   ---   /api/admin/stats";
    }
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDTO> stats() {
        return ResponseEntity.ok(adminService.getStats());
    }
}
