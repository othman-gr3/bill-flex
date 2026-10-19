package com.billflex.facturation.dto;

import com.billflex.facturation.model.InvoiceLine;

import java.math.BigDecimal;

public record InvoiceLineResponse(Long id, String libelle, int quantite,
                                  BigDecimal prixUnitaire, BigDecimal montant) {

    public static InvoiceLineResponse from(InvoiceLine l) {
        return new InvoiceLineResponse(l.getId(), l.getLibelle(), l.getQuantite(),
                l.getPrixUnitaire(), l.montant());
    }
}
