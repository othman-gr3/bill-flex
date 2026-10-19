package com.billflex.abonnements.dto;

import com.billflex.abonnements.model.Plan;

import java.math.BigDecimal;

public record PlanResponse(Long id, String nom, BigDecimal prixMensuel, int quotaUtilisateurs, boolean actif) {

    public static PlanResponse from(Plan p) {
        return new PlanResponse(p.getId(), p.getNom(), p.getPrixMensuel(), p.getQuotaUtilisateurs(), p.isActif());
    }
}
