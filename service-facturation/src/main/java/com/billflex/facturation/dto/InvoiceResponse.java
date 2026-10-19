package com.billflex.facturation.dto;

import com.billflex.facturation.model.Invoice;
import com.billflex.facturation.model.StatutInvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceResponse(Long id, Long subscriptionId, Long clientId, String periode,
                              BigDecimal montantHT, BigDecimal montantTTC, LocalDate echeance,
                              StatutInvoice statut, List<InvoiceLineResponse> lignes) {

    public static InvoiceResponse from(Invoice i) {
        return new InvoiceResponse(i.getId(), i.getSubscriptionId(), i.getClientId(), i.getPeriode(),
                i.getMontantHT(), i.getMontantTTC(), i.getEcheance(), i.getStatut(),
                i.getLignes().stream().map(InvoiceLineResponse::from).toList());
    }
}
