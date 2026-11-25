package com.billflex.clients.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_idempotency_key", columnNames = "idempotency_key"),
        indexes = @Index(name = "idx_payment_idempotency_key", columnList = "idempotency_key"))
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Lob
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String requestHash, Long resourceId,
                             Instant createdAt, String responseBody) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.resourceId = resourceId;
        this.createdAt = createdAt;
        this.responseBody = responseBody;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
