package com.billflex.facturation.service;

import com.billflex.facturation.dto.InvoiceDetailResponse;
import com.billflex.facturation.dto.InvoiceLineRequest;
import com.billflex.facturation.dto.InvoiceRequest;
import com.billflex.facturation.dto.InvoiceResponse;
import com.billflex.facturation.dto.NotificationEmailDto;
import com.billflex.facturation.exception.DuplicateResourceException;
import com.billflex.facturation.exception.InvalidStateException;
import com.billflex.facturation.exception.ResourceNotFoundException;
import com.billflex.facturation.feignclient.ClientDto;
import com.billflex.facturation.feignclient.PlanDto;
import com.billflex.facturation.feignclient.ResilientRequests;
import com.billflex.facturation.feignclient.SubscriptionDto;
import com.billflex.facturation.messaging.NotificationPublisher;
import com.billflex.facturation.model.Invoice;
import com.billflex.facturation.model.InvoiceLine;
import com.billflex.facturation.model.StatutInvoice;
import com.billflex.facturation.repository.InvoiceRepository;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository repository;
    private final ResilientRequests resilientRequests;
    private final NotificationPublisher notificationPublisher;

    @Transactional(readOnly = true)
    public List<InvoiceResponse> findAll() {
        return repository.findAll().stream().map(InvoiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse findById(Long id) {
        return InvoiceResponse.from(getOrThrow(id));
    }

    /**
     * Detailed view in tolerant mode: if a remote service is down, data comes from the cache
     * or the enrichment fields are null. If the rate limit is reached, only local data is returned.
     */
    @RateLimiter(name = "consultation", fallbackMethod = "consultationLimitee")
    @Transactional(readOnly = true)
    public InvoiceDetailResponse getDetail(Long id) {
        Invoice invoice = getOrThrow(id);

        ClientDto c = resilientRequests.getClientFb(invoice.getClientId());
        SubscriptionDto s = resilientRequests.getSubscriptionFb(invoice.getSubscriptionId());

        // planId is null when the subscription comes from the empty fallback: skip the plan call
        PlanDto p = s.planId() != null ? resilientRequests.getPlanFb(s.planId()) : null;

        return new InvoiceDetailResponse(
                InvoiceResponse.from(invoice),
                new InvoiceDetailResponse.ClientInfo(c.id(), c.nom(), c.email(), c.numeroTVA(), c.modePaiement()),
                new InvoiceDetailResponse.AbonnementInfo(s.id(), s.statut(),
                        p != null ? p.id() : null,
                        p != null ? p.nom() : null));
    }

    /** Degraded mode when the rate limit is reached: local data only, no remote call. */
    public InvoiceDetailResponse consultationLimitee(Long id, RequestNotPermitted cause) {
        return new InvoiceDetailResponse(InvoiceResponse.from(getOrThrow(id)), null, null);
    }

    public InvoiceResponse create(InvoiceRequest r) {
        // 1. Duplicate check BEFORE any remote call
        if (repository.existsBySubscriptionIdAndPeriode(r.subscriptionId(), r.periode())) {
            throw new DuplicateResourceException("Une facture existe déjà pour l'abonnement "
                    + r.subscriptionId() + " et la période " + r.periode());
        }

        // 2. Subscription must exist (404) and be ACTIVE; strict mode: 503 if service-abonnements is down
        SubscriptionDto sub = resilientRequests.getSubscriptionStrict(r.subscriptionId());
        if (!"ACTIVE".equals(sub.statut())) {
            throw new InvalidStateException("Abonnement non actif : " + sub.statut());
        }
        if (!sub.clientId().equals(r.clientId())) {
            throw new InvalidStateException("Le client ne correspond pas à l'abonnement");
        }

        // 3. Client must exist (404) and be ACTIF; strict mode: 503 if service-clients is down
        ClientDto client = resilientRequests.getClientStrict(r.clientId());
        if (!"ACTIF".equals(client.statut())) {
            throw new InvalidStateException("Client suspendu");
        }

        Invoice invoice = new Invoice();
        invoice.setSubscriptionId(r.subscriptionId());
        invoice.setClientId(r.clientId());
        invoice.setPeriode(r.periode());
        invoice.setEcheance(r.echeance());
        invoice.setStatut(StatutInvoice.BROUILLON);
        remplirLignes(invoice, r.lignes());
        return InvoiceResponse.from(repository.save(invoice));
    }

    /** Only a BROUILLON invoice can be modified. */
    public InvoiceResponse update(Long id, InvoiceRequest r) {
        Invoice invoice = getOrThrow(id);
        if (invoice.getStatut() != StatutInvoice.BROUILLON) {
            throw new InvalidStateException(
                    "Seule une facture BROUILLON peut être modifiée (statut actuel : " + invoice.getStatut() + ")");
        }
        if (repository.existsBySubscriptionIdAndPeriodeAndIdNot(r.subscriptionId(), r.periode(), id)) {
            throw new DuplicateResourceException("Une facture existe déjà pour l'abonnement "
                    + r.subscriptionId() + " et la période " + r.periode());
        }
        invoice.setSubscriptionId(r.subscriptionId());
        invoice.setClientId(r.clientId());
        invoice.setPeriode(r.periode());
        invoice.setEcheance(r.echeance());
        invoice.viderLignes();
        remplirLignes(invoice, r.lignes());
        return InvoiceResponse.from(repository.save(invoice));
    }

    /** Only a BROUILLON invoice can be deleted. */
    public void delete(Long id) {
        Invoice invoice = getOrThrow(id);
        if (invoice.getStatut() != StatutInvoice.BROUILLON) {
            throw new InvalidStateException(
                    "Seule une facture BROUILLON peut être supprimée (statut actuel : " + invoice.getStatut() + ")");
        }
        repository.delete(invoice);
    }

    public InvoiceResponse emettre(Long id) {
        Invoice invoice = getOrThrow(id);
        // Client must still be ACTIF before issuing; strict mode (503 if service-clients is down)
        ClientDto client = resilientRequests.getClientStrict(invoice.getClientId());
        if (!"ACTIF".equals(client.statut())) {
            throw new InvalidStateException("Client suspendu : émission impossible");
        }
        invoice.changerStatut(StatutInvoice.EMISE);
        Invoice saved = repository.save(invoice);
        if (client.email() != null && !client.email().isBlank()) {
            NotificationEmailDto notification = new NotificationEmailDto(
                    client.email(),
                    "Facture n° " + saved.getId() + " émise",
                    """
                            Bonjour %s,

                            Votre facture n° %d a été émise.
                            Période : %s
                            Montant TTC : %s
                            Échéance : %s
                            """.formatted(client.nom(), saved.getId(), saved.getPeriode(),
                            saved.getMontantTTC(), saved.getEcheance()));
            publierApresCommit(notification);
        } else {
            log.warn("Notification non publiée pour la facture {} : email client absent", saved.getId());
        }
        return InvoiceResponse.from(saved);
    }

    private void publierApresCommit(NotificationEmailDto notification) {
        Runnable publish = () -> {
            try {
                notificationPublisher.publier(notification);
            } catch (AmqpException ex) {
                log.error("Publication RabbitMQ impossible pour une notification email", ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    public InvoiceResponse payer(Long id) {
        return changerStatut(id, StatutInvoice.PAYEE);
    }

    public InvoiceResponse marquerEnRetard(Long id) {
        return changerStatut(id, StatutInvoice.EN_RETARD);
    }

    public InvoiceResponse annuler(Long id) {
        return changerStatut(id, StatutInvoice.ANNULEE);
    }

    private InvoiceResponse changerStatut(Long id, StatutInvoice cible) {
        Invoice invoice = getOrThrow(id);
        invoice.changerStatut(cible);
        return InvoiceResponse.from(repository.save(invoice));
    }

    private void remplirLignes(Invoice invoice, List<InvoiceLineRequest> lignes) {
        for (InvoiceLineRequest l : lignes) {
            invoice.addLigne(new InvoiceLine(l.libelle(), l.quantite(), l.prixUnitaire()));
        }
        invoice.recalculerMontants();
    }

    private Invoice getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture introuvable avec l'id " + id));
    }
}