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

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private final InvoiceRepository repository;

    @Transactional(readOnly = true)
    public List<InvoiceResponse> findAll() {
        return repository.findAll().stream().map(InvoiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse findById(Long id) {
        return InvoiceResponse.from(getOrThrow(id));
    }

    public InvoiceResponse create(InvoiceRequest r) {
        if (repository.existsBySubscriptionIdAndPeriode(r.subscriptionId(), r.periode())) {
            throw new DuplicateResourceException("Une facture existe déjà pour l'abonnement "
                    + r.subscriptionId() + " et la période " + r.periode());
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
        return changerStatut(id, StatutInvoice.EMISE);
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
