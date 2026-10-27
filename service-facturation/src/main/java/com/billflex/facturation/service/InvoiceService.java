package com.billflex.facturation.service;

import com.billflex.facturation.dto.InvoiceLineRequest;
import com.billflex.facturation.dto.InvoiceRequest;
import com.billflex.facturation.dto.InvoiceResponse;
import com.billflex.facturation.exception.DuplicateResourceException;
import com.billflex.facturation.exception.InvalidStateException;
import com.billflex.facturation.exception.ResourceNotFoundException;
import com.billflex.facturation.model.Invoice;
import com.billflex.facturation.model.InvoiceLine;
import com.billflex.facturation.model.StatutInvoice;
import com.billflex.facturation.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.billflex.facturation.dto.InvoiceDetailResponse;
import com.billflex.facturation.feignclient.AbonnementsClient;
import com.billflex.facturation.feignclient.ClientDto;
import com.billflex.facturation.feignclient.ClientsClient;
import com.billflex.facturation.feignclient.PlanDto;
import com.billflex.facturation.feignclient.SubscriptionDto;
import java.util.List;
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private final InvoiceRepository repository;
    private final AbonnementsClient abonnementsClient;
    private final ClientsClient clientsClient;

    @Transactional(readOnly = true)
    public List<InvoiceResponse> findAll() {
        return repository.findAll().stream().map(InvoiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse findById(Long id) {
        return InvoiceResponse.from(getOrThrow(id));
    }

    /** Invoice enriched with client and subscription data fetched through Feign. */
    @Transactional(readOnly = true)
    public InvoiceDetailResponse getDetail(Long id) {
        Invoice invoice = getOrThrow(id);

        ClientDto c = clientsClient.getClient(invoice.getClientId());
        SubscriptionDto s = abonnementsClient.getSubscription(invoice.getSubscriptionId());
        PlanDto p = abonnementsClient.getPlan(s.planId());

        return new InvoiceDetailResponse(
                InvoiceResponse.from(invoice),
                new InvoiceDetailResponse.ClientInfo(c.id(), c.nom(), c.email(), c.numeroTVA(), c.modePaiement()),
                new InvoiceDetailResponse.AbonnementInfo(s.id(), s.statut(), p.id(), p.nom()));
    }

    public InvoiceResponse create(InvoiceRequest r) {
        // 1. Duplicate check BEFORE any remote call
        if (repository.existsBySubscriptionIdAndPeriode(r.subscriptionId(), r.periode())) {
            throw new DuplicateResourceException("Une facture existe déjà pour l'abonnement "
                    + r.subscriptionId() + " et la période " + r.periode());
        }

        // 2. Subscription must exist (404 via Feign) and be ACTIVE
        SubscriptionDto sub = abonnementsClient.getSubscription(r.subscriptionId());
        if (!"ACTIVE".equals(sub.statut())) {
            throw new InvalidStateException("Abonnement non actif : " + sub.statut());
        }
        if (!sub.clientId().equals(r.clientId())) {
            throw new InvalidStateException("Le client ne correspond pas à l'abonnement");
        }

        // 3. Client must exist (404 via Feign) and be ACTIF
        ClientDto client = clientsClient.getClient(r.clientId());
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
        // Client must still be ACTIF before issuing the invoice
        ClientDto client = clientsClient.getClient(invoice.getClientId());
        if (!"ACTIF".equals(client.statut())) {
            throw new InvalidStateException("Client suspendu : émission impossible");
        }
        invoice.changerStatut(StatutInvoice.EMISE);
        return InvoiceResponse.from(repository.save(invoice));
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