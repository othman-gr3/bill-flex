package com.billflex.clients.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull(message = "clientId est obligatoire")
        Long clientId,

        @NotNull(message = "invoiceId est obligatoire")
        Long invoiceId,

        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0.01", message = "Le montant doit être supérieur ou égal à 0.01")
        BigDecimal montant,

        @NotBlank(message = "Le moyen de paiement est obligatoire")
        String moyen
) {}
