package com.omar.bankapi.service;

import com.omar.bankapi.dto.AdminStatsDTO;
import com.omar.bankapi.repository.AccountRepository;
import com.omar.bankapi.repository.TransactionRepository;
import com.omar.bankapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AdminStatsDTO getStats() {
        return new AdminStatsDTO(
                userRepository.count(),
                accountRepository.count(),
                transactionRepository.count(),
                accountRepository.sumBalances()
        );
    }
}
