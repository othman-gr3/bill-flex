package com.billflex.abonnements.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "plans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nom;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal prixMensuel;

    @Column(nullable = false)
    private int quotaUtilisateurs;

    @Column(nullable = false)
    private boolean actif;
}
