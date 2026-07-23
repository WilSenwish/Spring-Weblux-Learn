package com.example.accounting.repository;

import com.example.accounting.entity.BillDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface ReactiveBillDocumentRepository extends ReactiveMongoRepository<BillDocument, String> {

    Mono<BillDocument> findByMysqlId(Long mysqlId);
}
