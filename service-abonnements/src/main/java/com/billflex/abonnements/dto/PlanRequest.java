package com.billflex.abonnements.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PlanRequest(
        @NotBlank(message = "Le nom du plan est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String nom,

        @NotNull(message = "Le prix mensuel est obligatoire")
        @DecimalMin(value = "0.0", message = "Le prix mensuel doit être positif ou nul")
        BigDecimal prixMensuel,

        @NotNull(message = "Le quota d'utilisateurs est obligatoire")
        @Min(value = 1, message = "Le quota d'utilisateurs doit être au moins 1")
        Integer quotaUtilisateurs,

        @NotNull(message = "Le champ actif est obligatoire")
        Boolean actif
) {}
