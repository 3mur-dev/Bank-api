package com.omar.bankapi.repository;

import com.omar.bankapi.model.Account;
import com.omar.bankapi.model.enums.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    boolean existsByAccountNumber(String accountNumber);

    boolean existsByUserIdAndActiveTrue(Long userId);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select case when count(t) > 0 then true else false end
            from Transaction t
            where (t.sender.id in :accountIds or t.receiver.id in :accountIds)
              and t.status = :status
            """)
    boolean existsPendingTransactionsForAccounts(@Param("accountIds") Collection<Long> accountIds,
                                                 @Param("status") TransactionStatus status);
}
