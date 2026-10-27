package com.billflex.facturation.dto;

public record InvoiceDetailResponse(InvoiceResponse facture, ClientInfo client, AbonnementInfo abonnement) {

    public record ClientInfo(Long id, String nom, String email, String numeroTVA, String modePaiement) {}

    public record AbonnementInfo(Long id, String statut, Long planId, String planNom) {}
}