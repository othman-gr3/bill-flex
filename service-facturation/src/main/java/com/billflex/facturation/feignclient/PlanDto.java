package com.billflex.facturation.feignclient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanDto(Long id, String nom, BigDecimal prixMensuel) {}