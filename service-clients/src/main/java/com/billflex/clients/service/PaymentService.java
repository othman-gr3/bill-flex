package com.billflex.clients.service;

import com.billflex.clients.dto.PaymentRequest;
import com.billflex.clients.dto.PaymentResponse;
import com.billflex.clients.exception.InvalidStateException;
import com.billflex.clients.exception.ResourceNotFoundException;
import com.billflex.clients.model.Client;
import com.billflex.clients.model.Payment;
import com.billflex.clients.model.StatutPayment;
import com.billflex.clients.repository.ClientRepository;
import com.billflex.clients.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public List<PaymentResponse> findAll() {
        return paymentRepository.findAll().stream().map(PaymentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(Long id) {
        return PaymentResponse.from(getOrThrow(id));
    }

    public PaymentResponse create(PaymentRequest r) {
        Client client = clientRepository.findById(r.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client introuvable avec l'id " + r.clientId()));
        Payment payment = Payment.builder()
                .client(client)
                .invoiceId(r.invoiceId())
                .montant(r.montant())
                .moyen(r.moyen())
                .date(LocalDate.now())
                .statut(StatutPayment.EN_ATTENTE)
                .build();
        return PaymentResponse.from(paymentRepository.save(payment));
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
