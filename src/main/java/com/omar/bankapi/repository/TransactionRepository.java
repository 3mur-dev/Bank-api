package com.omar.bankapi.repository;

import com.omar.bankapi.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    long deleteBySenderIdInOrReceiverIdIn(List<Long> senderIds, List<Long> receiverIds);

}