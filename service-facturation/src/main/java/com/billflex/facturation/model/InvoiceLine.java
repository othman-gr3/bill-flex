package com.billflex.facturation.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "invoice_lines")
@Getter @Setter @NoArgsConstructor
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(nullable = false)
    private String libelle;

    @Column(nullable = false)
    private int quantite;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal prixUnitaire;

    public InvoiceLine(String libelle, int quantite, BigDecimal prixUnitaire) {
        this.libelle = libelle;
        this.quantite = quantite;
        this.prixUnitaire = prixUnitaire;
    }

    /** quantite x prixUnitaire, scale 2. */
    public BigDecimal montant() {
        return prixUnitaire.multiply(BigDecimal.valueOf(quantite)).setScale(2, RoundingMode.HALF_UP);
    }
}
