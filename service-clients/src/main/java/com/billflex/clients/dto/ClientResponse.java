package com.billflex.clients.dto;

import com.billflex.clients.model.Client;
import com.billflex.clients.model.ModePaiement;
import com.billflex.clients.model.StatutClient;

public record ClientResponse(Long id, String nom, String email, String numeroTVA,
                             ModePaiement modePaiement, StatutClient statut) {

    public static ClientResponse from(Client c) {
        return new ClientResponse(c.getId(), c.getNom(), c.getEmail(), c.getNumeroTVA(),
                c.getModePaiement(), c.getStatut());
    }
}
