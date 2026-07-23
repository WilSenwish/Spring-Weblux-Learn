package com.example.accounting.repository;

import com.example.accounting.entity.BillDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillDocumentRepository extends MongoRepository<BillDocument, String> {
    Optional<BillDocument> findByMysqlId(Long mysqlId);
    void deleteByMysqlId(Long mysqlId);
}
