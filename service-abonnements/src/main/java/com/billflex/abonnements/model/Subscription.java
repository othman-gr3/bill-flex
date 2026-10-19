package com.billflex.abonnements.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "subscriptions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    /** Reference to a client of service-clients (plain id, no cross-service relation). */
    @Column(nullable = false)
    private Long clientId;

    @Column(nullable = false)
    private LocalDate dateDebut;

    private LocalDate dateFin;

    @Column(nullable = false)
    private LocalDate prochaineEcheance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutSubscription statut;
}
