package com.billflex.clients.dto;

import com.billflex.clients.model.Payment;
import com.billflex.clients.model.StatutPayment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentResponse(Long id, Long clientId, Long invoiceId, LocalDate date,
                              BigDecimal montant, String moyen, StatutPayment statut) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getClient().getId(), p.getInvoiceId(), p.getDate(),
                p.getMontant(), p.getMoyen(), p.getStatut());
    }
}
