package com.billflex.abonnements.repository;

import com.billflex.abonnements.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    boolean existsByNom(String nom);
    boolean existsByNomAndIdNot(String nom, Long id);
}
