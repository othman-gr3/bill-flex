package com.billflex.abonnements.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SubscriptionRequest(
        @NotNull(message = "planId est obligatoire")
        Long planId,

        @NotNull(message = "clientId est obligatoire")
        Long clientId,

        @NotNull(message = "La date de début est obligatoire")
        LocalDate dateDebut
) {}
