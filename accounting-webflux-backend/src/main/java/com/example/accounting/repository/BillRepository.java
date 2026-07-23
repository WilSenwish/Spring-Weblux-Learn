package com.example.accounting.repository;

import com.example.accounting.entity.Bill;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface BillRepository extends ReactiveCrudRepository<Bill, Long> {
    Flux<Bill> findByUserId(Long userId);
}
