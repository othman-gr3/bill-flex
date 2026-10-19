package com.billflex.clients.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    /** Reference to an invoice of service-facturation (plain id, no cross-service relation). */
    @Column(nullable = false)
    private Long invoiceId;

    @Column(name = "date_paiement", nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false)
    private String moyen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutPayment statut;
}
