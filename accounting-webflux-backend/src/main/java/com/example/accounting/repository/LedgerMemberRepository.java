package com.example.accounting.repository;

import com.example.accounting.entity.LedgerMember;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LedgerMemberRepository extends R2dbcRepository<LedgerMember, Long> {
    Flux<LedgerMember> findByLedgerId(Long ledgerId);
    Flux<LedgerMember> findByUserId(Long userId);
    Mono<LedgerMember> findByLedgerIdAndUserId(Long ledgerId, Long userId);
    Mono<Long> deleteByLedgerIdAndUserId(Long ledgerId, Long userId);
}
