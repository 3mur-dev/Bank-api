package com.omar.bankapi.repository;

import com.omar.bankapi.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    boolean existsByAccountNumber(String accountNumber);

    void deleteByUserId(Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Account a set a.balance = a.balance + :amount " +
            "where a.id = :id and a.active = true")
    int incrementBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Account a set a.balance = a.balance - :amount " +
            "where a.id = :id and a.active = true and a.balance >= :amount")
    int decrementBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    List<Account> findByUserId(Long userId);

    @Query("select coalesce(sum(a.balance), 0) from Account a")
    BigDecimal sumBalances();
}
