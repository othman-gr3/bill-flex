package com.billflex.facturation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InvoiceLineRequest(
        @NotBlank(message = "Le libellé est obligatoire")
        String libelle,

        @NotNull(message = "La quantité est obligatoire")
        @Min(value = 1, message = "La quantité doit être au moins 1")
        Integer quantite,

        @NotNull(message = "Le prix unitaire est obligatoire")
        @DecimalMin(value = "0.0", message = "Le prix unitaire doit être positif ou nul")
        BigDecimal prixUnitaire
) {}
