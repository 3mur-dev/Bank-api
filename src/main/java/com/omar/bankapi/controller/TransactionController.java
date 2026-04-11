package com.omar.bankapi.controller;

import com.omar.bankapi.dto.TransactionDTO;
import com.omar.bankapi.service.TransactionService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.omar.bankapi.dto.*;
import com.omar.bankapi.model.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<Page<TransactionDTO>> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Boolean fraud) {

        return ResponseEntity.ok(
                transactionService.getTransactions(page, size, accountId, status, type, fraud)
        );
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<TransactionDTO> approveTransaction(@PathVariable Long id) {

        return ResponseEntity.ok(
                transactionService.approveTransaction(id)
        );
    }
    @PostMapping("/{id}/reject")
    public ResponseEntity<TransactionDTO> rejectTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(
                transactionService.rejectTransaction(id)
        );
    }
}