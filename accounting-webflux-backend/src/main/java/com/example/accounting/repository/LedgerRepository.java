package com.example.accounting.repository;

import com.example.accounting.entity.Ledger;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface LedgerRepository extends R2dbcRepository<Ledger, Long> {
    Flux<Ledger> findByOwnerId(Long ownerId);
}
