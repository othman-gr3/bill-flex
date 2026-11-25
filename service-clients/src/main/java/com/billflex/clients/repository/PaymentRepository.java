package com.billflex.clients.repository;

import com.billflex.clients.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Transactional(readOnly = true)
    long countByClient_IdAndInvoiceId(Long clientId, Long invoiceId);
}
