package com.billflex.clients.service;

import com.billflex.clients.dto.PaymentRequest;
import com.billflex.clients.dto.PaymentResponse;
import com.billflex.clients.exception.InvalidStateException;
import com.billflex.clients.exception.ResourceNotFoundException;
import com.billflex.clients.exception.DuplicateResourceException;
import com.billflex.clients.model.IdempotencyRecord;
import com.billflex.clients.model.Payment;
import com.billflex.clients.model.StatutPayment;
import com.billflex.clients.repository.IdempotencyRecordRepository;
import com.billflex.clients.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentCreationTransaction paymentCreationTransaction;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<PaymentResponse> findAll() {
        return paymentRepository.findAll().stream().map(PaymentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(Long id) {
        return PaymentResponse.from(getOrThrow(id));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreationResult<PaymentResponse> create(String idempotencyKey, PaymentRequest request) {
        String key = idempotencyKey.trim();
        String requestHash = hashRequest(request);
        try {
            return paymentCreationTransaction.create(key, requestHash, request);
        } catch (DataIntegrityViolationException ex) {
            IdempotencyRecord concurrentRecord = idempotencyRecordRepository.findByIdempotencyKey(key)
                    .orElseThrow(() -> ex);
            return replayOrConflict(concurrentRecord, requestHash);
        }
    }

    private String hashRequest(PaymentRequest request) {
        return CanonicalRequestHasher.sha256(request, objectMapper);
    }

    CreationResult<PaymentResponse> replayOrConflict(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new DuplicateResourceException(
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

    /** EN_ATTENTE -> PAYE, any other state is rejected (409). */
    public PaymentResponse payer(Long id) {
        Payment payment = getOrThrow(id);
        if (payment.getStatut() != StatutPayment.EN_ATTENTE) {
            throw new InvalidStateException(
                    "Seul un paiement EN_ATTENTE peut être payé (statut actuel : " + payment.getStatut() + ")");
        }
        payment.setStatut(StatutPayment.PAYE);
        payment.setDate(LocalDate.now());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    private Payment getOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable avec l'id " + id));
    }
}
