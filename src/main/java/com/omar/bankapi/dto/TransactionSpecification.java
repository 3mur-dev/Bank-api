package com.omar.bankapi.dto;

import com.omar.bankapi.model.Transaction;
import com.omar.bankapi.model.enums.TransactionStatus;
import com.omar.bankapi.model.enums.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;

public class TransactionSpecification {

    private TransactionSpecification() {
    }

    public static Specification<Transaction> filter(
            Long accountId,
            TransactionStatus status,
            TransactionType type,
            Boolean fraud
    ) {
        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (accountId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("sender").get("id"), accountId),
                        cb.equal(root.get("receiver").get("id"), accountId)
                ));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            if (fraud != null) {
                predicates.add(cb.equal(root.get("isFlagged"), fraud));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
