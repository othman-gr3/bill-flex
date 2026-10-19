package com.billflex.abonnements.service;

import com.billflex.abonnements.dto.PlanRequest;
import com.billflex.abonnements.dto.PlanResponse;
import com.billflex.abonnements.exception.DuplicateResourceException;
import com.billflex.abonnements.exception.ResourceNotFoundException;
import com.billflex.abonnements.model.Plan;
import com.billflex.abonnements.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanService {

    private final PlanRepository repository;

    @Transactional(readOnly = true)
    public List<PlanResponse> findAll() {
        return repository.findAll().stream().map(PlanResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse findById(Long id) {
        return PlanResponse.from(getOrThrow(id));
    }

    public PlanResponse create(PlanRequest r) {
        if (repository.existsByNom(r.nom())) {
            throw new DuplicateResourceException("Un plan nommé '" + r.nom() + "' existe déjà");
        }
        Plan plan = Plan.builder()
                .nom(r.nom())
                .prixMensuel(r.prixMensuel())
                .quotaUtilisateurs(r.quotaUtilisateurs())
                .actif(r.actif())
                .build();
        return PlanResponse.from(repository.save(plan));
    }

    public PlanResponse update(Long id, PlanRequest r) {
        Plan plan = getOrThrow(id);
        if (repository.existsByNomAndIdNot(r.nom(), id)) {
            throw new DuplicateResourceException("Un plan nommé '" + r.nom() + "' existe déjà");
        }
        plan.setNom(r.nom());
        plan.setPrixMensuel(r.prixMensuel());
        plan.setQuotaUtilisateurs(r.quotaUtilisateurs());
        plan.setActif(r.actif());
        return PlanResponse.from(repository.save(plan));
    }

    public void delete(Long id) {
        repository.delete(getOrThrow(id));
        repository.flush(); // forces the FK check now, so a plan used by subscriptions gives a clean 409
    }

    private Plan getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan introuvable avec l'id " + id));
    }
}
