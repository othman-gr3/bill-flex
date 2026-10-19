package com.billflex.facturation.repository;

import com.billflex.facturation.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    boolean existsBySubscriptionIdAndPeriode(Long subscriptionId, String periode);
    boolean existsBySubscriptionIdAndPeriodeAndIdNot(Long subscriptionId, String periode, Long id);
}
