package com.billflex.abonnements.dto;

import com.billflex.abonnements.model.StatutSubscription;
import com.billflex.abonnements.model.Subscription;

import java.time.LocalDate;

public record SubscriptionResponse(Long id, Long planId, String planNom, Long clientId,
                                   LocalDate dateDebut, LocalDate dateFin,
                                   LocalDate prochaineEcheance, StatutSubscription statut) {

    public static SubscriptionResponse from(Subscription s) {
        return new SubscriptionResponse(s.getId(), s.getPlan().getId(), s.getPlan().getNom(), s.getClientId(),
                s.getDateDebut(), s.getDateFin(), s.getProchaineEcheance(), s.getStatut());
    }
}
