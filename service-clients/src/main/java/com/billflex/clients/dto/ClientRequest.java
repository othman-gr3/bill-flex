package com.billflex.clients.dto;

import com.billflex.clients.model.ModePaiement;
import jakarta.validation.constraints.*;

public record ClientRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String nom,

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Email invalide")
        String email,

        @NotBlank(message = "Le numéro de TVA est obligatoire")
        @Pattern(regexp = "^[A-Z]{2}[A-Z0-9]{8,12}$", message = "Numéro de TVA invalide (ex: MA12345678)")
        String numeroTVA,

        @NotNull(message = "Le mode de paiement est obligatoire")
        ModePaiement modePaiement
) {}
