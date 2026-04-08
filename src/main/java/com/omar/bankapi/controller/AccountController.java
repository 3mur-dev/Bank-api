package com.omar.bankapi.controller;

import com.omar.bankapi.dto.AccountDTO;
import com.omar.bankapi.dto.CreateAccountDTO;
import com.omar.bankapi.dto.UpdateAccountDTO;
import com.omar.bankapi.dto.TransactionAmountDTO;
import com.omar.bankapi.dto.TransferDTO;
import com.omar.bankapi.service.AccountService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public List<AccountDTO> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountDTO> getAccountById(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createAccount(@Valid @RequestBody CreateAccountDTO dto) {
        CreateAccountDTO created = accountService.createAccount(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@PathVariable Long id) {
        accountService.deleteAccount(id);
        return ResponseEntity.ok(Map.of("message", "account deleted successfully"));
    }
    @PutMapping("/{id}")
    public ResponseEntity<AccountDTO> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccountDTO dto) {

        return ResponseEntity.ok(accountService.updateAccount(id, dto));
    }
    @PostMapping("/{id}/deposit")
    public ResponseEntity<AccountDTO> deposit(
            @PathVariable Long id,
            @Valid @RequestBody TransactionAmountDTO dto) {
        return ResponseEntity.ok(accountService.deposit(id, dto));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<AccountDTO> withdraw(
            @PathVariable Long id,
            @Valid @RequestBody TransactionAmountDTO dto) {
        return ResponseEntity.ok(accountService.withdraw(id, dto));
    }

    @PostMapping("/{id}/transfer")
    public ResponseEntity<AccountDTO> transfer(
            @PathVariable Long id,
            @Valid @RequestBody TransferDTO dto) {
        return ResponseEntity.ok(accountService.transfer(id, dto));
    }

}
