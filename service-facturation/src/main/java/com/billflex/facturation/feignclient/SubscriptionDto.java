package com.billflex.facturation.feignclient;



import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SubscriptionDto(Long id, Long clientId, Long planId, String statut) {}