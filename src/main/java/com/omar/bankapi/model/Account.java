package com.omar.bankapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Transactions this account sent
    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL)
    private Set<Transaction> sentTransactions = new HashSet<>();

    // Transactions this account received
    @OneToMany(mappedBy = "receiver", cascade = CascadeType.ALL)
    private Set<Transaction> receivedTransactions = new HashSet<>();

    @Column(nullable = false)
    private boolean active = true;
}
