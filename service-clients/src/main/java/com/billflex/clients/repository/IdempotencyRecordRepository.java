package com.billflex.clients.repository;

import com.billflex.clients.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    @Transactional(readOnly = true)
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}
