package com.billflex.abonnements.service;

import com.billflex.abonnements.dto.SubscriptionRequest;
import com.billflex.abonnements.dto.SubscriptionResponse;
import com.billflex.abonnements.exception.InvalidStateException;
import com.billflex.abonnements.exception.ResourceNotFoundException;
import com.billflex.abonnements.model.Plan;
import com.billflex.abonnements.model.StatutSubscription;
import com.billflex.abonnements.model.Subscription;
import com.billflex.abonnements.repository.PlanRepository;
import com.billflex.abonnements.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> findAll() {
        return subscriptionRepository.findAll().stream().map(SubscriptionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse findById(Long id) {
        return SubscriptionResponse.from(getOrThrow(id));
    }

    public SubscriptionResponse create(SubscriptionRequest r) {
        Plan plan = getActivePlan(r.planId());
        Subscription subscription = Subscription.builder()
                .plan(plan)
                .clientId(r.clientId())
                .dateDebut(r.dateDebut())
                .prochaineEcheance(r.dateDebut().plusMonths(1))
                .statut(StatutSubscription.ACTIVE)
                .build();
        return SubscriptionResponse.from(subscriptionRepository.save(subscription));
    }

    public SubscriptionResponse update(Long id, SubscriptionRequest r) {
        Subscription subscription = getOrThrow(id);
        if (subscription.getStatut() == StatutSubscription.RESILIEE) {
            throw new InvalidStateException("Un abonnement résilié ne peut plus être modifié");
        }
        subscription.setPlan(getActivePlan(r.planId()));
        subscription.setClientId(r.clientId());
        subscription.setDateDebut(r.dateDebut());
        subscription.setProchaineEcheance(r.dateDebut().plusMonths(1));
        return SubscriptionResponse.from(subscriptionRepository.save(subscription));
    }

    public void delete(Long id) {
        subscriptionRepository.delete(getOrThrow(id));
    }

    /** Sets the status to RESILIEE and the end date to today (409 if already terminated). */
    public SubscriptionResponse resilier(Long id) {
        Subscription subscription = getOrThrow(id);
        if (subscription.getStatut() == StatutSubscription.RESILIEE) {
            throw new InvalidStateException("L'abonnement " + id + " est déjà résilié");
        }
        subscription.setStatut(StatutSubscription.RESILIEE);
        subscription.setDateFin(LocalDate.now());
        return SubscriptionResponse.from(subscriptionRepository.save(subscription));
    }

    private Plan getActivePlan(Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan introuvable avec l'id " + planId));
        if (!plan.isActif()) {
            throw new InvalidStateException("Le plan '" + plan.getNom() + "' n'est plus actif");
        }
        return plan;
    }

    private Subscription getOrThrow(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable avec l'id " + id));
    }
}
