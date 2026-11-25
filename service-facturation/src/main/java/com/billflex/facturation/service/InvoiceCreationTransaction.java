package com.billflex.facturation.service;

import com.billflex.facturation.dto.InvoiceLineRequest;
import com.billflex.facturation.dto.InvoiceRequest;
import com.billflex.facturation.dto.InvoiceResponse;
import com.billflex.facturation.exception.DuplicateResourceException;
import com.billflex.facturation.exception.InvalidStateException;
import com.billflex.facturation.feignclient.ClientDto;
import com.billflex.facturation.feignclient.ResilientRequests;
import com.billflex.facturation.feignclient.SubscriptionDto;
import com.billflex.facturation.model.IdempotencyRecord;
import com.billflex.facturation.model.Invoice;
import com.billflex.facturation.model.InvoiceLine;
import com.billflex.facturation.model.StatutInvoice;
import com.billflex.facturation.repository.IdempotencyRecordRepository;
import com.billflex.facturation.repository.InvoiceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceCreationTransaction {

    private final InvoiceRepository invoiceRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ResilientRequests resilientRequests;
    private final ObjectMapper objectMapper;

    @Transactional
    public CreationResult<InvoiceResponse> create(String key, String requestHash, InvoiceRequest request) {
        var existing = idempotencyRecordRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), requestHash);
        }

        if (invoiceRepository.existsBySubscriptionIdAndPeriode(request.subscriptionId(), request.periode())) {
            throw new DuplicateResourceException("Une facture existe déjà pour l'abonnement "
                    + request.subscriptionId() + " et la période " + request.periode());
        }

        SubscriptionDto subscription = resilientRequests.getSubscriptionStrict(request.subscriptionId());
        if (!"ACTIVE".equals(subscription.statut())) {
            throw new InvalidStateException("Abonnement non actif : " + subscription.statut());
        }
        if (!subscription.clientId().equals(request.clientId())) {
            throw new InvalidStateException("Le client ne correspond pas à l'abonnement");
        }

        ClientDto client = resilientRequests.getClientStrict(request.clientId());
        if (!"ACTIF".equals(client.statut())) {
            throw new InvalidStateException("Client suspendu");
        }

        Invoice invoice = new Invoice();
        invoice.setSubscriptionId(request.subscriptionId());
        invoice.setClientId(request.clientId());
        invoice.setPeriode(request.periode());
        invoice.setEcheance(request.echeance());
        invoice.setStatut(StatutInvoice.BROUILLON);
        remplirLignes(invoice, request.lignes());
        Invoice saved = invoiceRepository.saveAndFlush(invoice);
        InvoiceResponse response = InvoiceResponse.from(saved);
        idempotencyRecordRepository.saveAndFlush(new IdempotencyRecord(
                key, requestHash, saved.getId(), Instant.now(), serialize(response)));
        return new CreationResult<>(response, false);
    }

    private CreationResult<InvoiceResponse> resolveExisting(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new DuplicateResourceException(
                    "Cette clé Idempotency-Key a déjà été utilisée avec une requête différente");
        }
        try {
            InvoiceResponse snapshot = objectMapper.readValue(record.getResponseBody(), InvoiceResponse.class);
            InvoiceResponse original = new InvoiceResponse(record.getResourceId(), snapshot.subscriptionId(),
                    snapshot.clientId(), snapshot.periode(), snapshot.montantHT(), snapshot.montantTTC(),
                    snapshot.echeance(), snapshot.statut(), snapshot.lignes());
            return new CreationResult<>(original, true);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Impossible de relire la réponse idempotente", ex);
        }
    }

    private String serialize(InvoiceResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Impossible d'enregistrer la réponse idempotente", ex);
        }
    }

    private void remplirLignes(Invoice invoice, List<InvoiceLineRequest> lignes) {
        for (InvoiceLineRequest ligne : lignes) {
            invoice.addLigne(new InvoiceLine(ligne.libelle(), ligne.quantite(), ligne.prixUnitaire()));
        }
        invoice.recalculerMontants();
    }
}
