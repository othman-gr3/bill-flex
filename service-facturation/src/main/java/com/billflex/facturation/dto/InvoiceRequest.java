package com.billflex.facturation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;

public record InvoiceRequest(
        @NotNull(message = "subscriptionId est obligatoire")
        Long subscriptionId,

        @NotNull(message = "clientId est obligatoire")
        Long clientId,

        @NotNull(message = "La période est obligatoire")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Période invalide (format attendu : yyyy-MM)")
        String periode,

        @NotNull(message = "L'échéance est obligatoire")
        @FutureOrPresent(message = "L'échéance ne peut pas être dans le passé")
        LocalDate echeance,

        @NotEmpty(message = "Une facture doit contenir au moins une ligne")
        @Valid
        List<InvoiceLineRequest> lignes
) {}
