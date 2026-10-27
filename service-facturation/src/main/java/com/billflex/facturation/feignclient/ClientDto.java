package com.billflex.facturation.feignclient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientDto(Long id, String nom, String email, String numeroTVA,
                        String modePaiement, String statut) {}