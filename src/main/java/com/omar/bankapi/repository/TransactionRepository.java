package com.omar.bankapi.repository;

import com.omar.bankapi.model.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.omar.bankapi.model.enums.TransactionStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    @Query("""
            select case when count(t) > 0 then true else false end
            from Transaction t
            where (t.sender.id in :accountIds or t.receiver.id in :accountIds)
              and t.status = :status
            """)
    boolean existsByAccountIdsAndStatus(@Param("accountIds") List<Long> accountIds,
                                        @Param("status") TransactionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") Long id);

}
