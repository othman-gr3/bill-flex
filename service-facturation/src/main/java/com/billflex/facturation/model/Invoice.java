package com.billflex.facturation.model;

import com.billflex.facturation.exception.InvalidStateException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices",
        uniqueConstraints = @UniqueConstraint(columnNames = {"subscription_id", "periode"}))
@Getter @Setter @NoArgsConstructor
public class Invoice {

    /** VAT 20 % -> TTC = HT x 1.20 */
    public static final BigDecimal COEFF_TTC = new BigDecimal("1.20");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference to a subscription of service-abonnements (plain id). */
    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    /** Reference to a client of service-clients (plain id). */
    @Column(nullable = false)
    private Long clientId;

    /** Billing period, format yyyy-MM (e.g. 2026-10). */
    @Column(nullable = false, length = 7)
    private String periode;

    @Column(name = "montant_ht", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantHT = BigDecimal.ZERO;

    @Column(name = "montant_ttc", nullable = false, precision = 12, scale = 2)
    private BigDecimal montantTTC = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate echeance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutInvoice statut = StatutInvoice.BROUILLON;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLine> lignes = new ArrayList<>();

    public void addLigne(InvoiceLine ligne) {
        ligne.setInvoice(this);
        lignes.add(ligne);
    }

    public void viderLignes() {
        lignes.clear();
    }

    /** Recomputes HT and TTC from the lines (never trusted from the client). */
    public void recalculerMontants() {
        BigDecimal ht = lignes.stream()
                .map(InvoiceLine::montant)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        this.montantHT = ht;
        this.montantTTC = ht.multiply(COEFF_TTC).setScale(2, RoundingMode.HALF_UP);
    }

    /** Applies a status transition or throws InvalidStateException (HTTP 409). */
    public void changerStatut(StatutInvoice cible) {
        if (!statut.peutPasserA(cible)) {
            throw new InvalidStateException(
                    "Transition interdite : " + statut + " -> " + cible);
        }
        this.statut = cible;
    }
}
