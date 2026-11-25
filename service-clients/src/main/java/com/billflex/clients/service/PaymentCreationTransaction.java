package com.billflex.clients.service;

import com.billflex.clients.dto.PaymentRequest;
import com.billflex.clients.dto.PaymentResponse;
import com.billflex.clients.exception.ResourceNotFoundException;
import com.billflex.clients.model.Client;
import com.billflex.clients.model.IdempotencyRecord;
import com.billflex.clients.model.Payment;
import com.billflex.clients.model.StatutPayment;
import com.billflex.clients.repository.ClientRepository;
import com.billflex.clients.repository.IdempotencyRecordRepository;
import com.billflex.clients.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PaymentCreationTransaction {

    private final PaymentRepository paymentRepository;
    private final ClientRepository clientRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CreationResult<PaymentResponse> create(String key, String requestHash, PaymentRequest request) {
        var existing = idempotencyRecordRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), requestHash);
        }

        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client introuvable avec l'id " + request.clientId()));
        Payment payment = Payment.builder()
                .client(client)
                .invoiceId(request.invoiceId())
                .montant(request.montant())
                .moyen(request.moyen())
                .date(LocalDate.now())
                .statut(StatutPayment.EN_ATTENTE)
                .build();
        Payment saved = paymentRepository.saveAndFlush(payment);
        PaymentResponse response = PaymentResponse.from(saved);
        idempotencyRecordRepository.saveAndFlush(new IdempotencyRecord(
                key, requestHash, saved.getId(), Instant.now(), serialize(response)));
        return new CreationResult<>(response, false);
    }

    private CreationResult<PaymentResponse> resolveExisting(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new com.billflex.clients.exception.DuplicateResourceException(
                    "Cette clé Idempotency-Key a déjà été utilisée avec une requête différente");
        }
        try {
            PaymentResponse snapshot = objectMapper.readValue(record.getResponseBody(), PaymentResponse.class);
            PaymentResponse original = new PaymentResponse(record.getResourceId(), snapshot.clientId(),
                    snapshot.invoiceId(), snapshot.date(), snapshot.montant(), snapshot.moyen(), snapshot.statut());
            return new CreationResult<>(original, true);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Impossible de relire la réponse idempotente", ex);
        }
    }

    private String serialize(PaymentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Impossible d'enregistrer la réponse idempotente", ex);
        }
    }
}
