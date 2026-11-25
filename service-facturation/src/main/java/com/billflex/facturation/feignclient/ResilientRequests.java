package com.billflex.facturation.feignclient;

import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import com.billflex.facturation.exception.ServiceExterneIndisponibleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientRequests {

    private final ClientsClient clientsClient;
    private final AbonnementsClient abonnementsClient;
    private final CacheManager cacheManager;

    // ========== Reading: tolerant fallback (cache or empty object) ==========

    @Retry(name = "service-clients", fallbackMethod = "clientDeRepli")
    @CircuitBreaker(name = "service-clients")
    @Bulkhead(name = "service-clients")
    public ClientDto getClientFb(Long id) {
        ClientDto client = clientsClient.getClient(id);
        mettreEnCache("clients", id, client);
        return client;
    }

    @Retry(name = "service-abonnements", fallbackMethod = "abonnementDeRepli")
    @CircuitBreaker(name = "service-abonnements")
    @Bulkhead(name = "service-abonnements")
    public SubscriptionDto getSubscriptionFb(Long id) {
        SubscriptionDto sub = abonnementsClient.getSubscription(id);
        mettreEnCache("abonnements", id, sub);
        return sub;
    }

    @Retry(name = "service-abonnements", fallbackMethod = "planDeRepli")
    @CircuitBreaker(name = "service-abonnements")
    @Bulkhead(name = "service-abonnements")
    public PlanDto getPlanFb(Long id) {
        PlanDto plan = abonnementsClient.getPlan(id);
        mettreEnCache("plans", id, plan);
        return plan;
    }

    // ========== Creation: strict fallback (503 exception) ==========

    @Retry(name = "service-clients", fallbackMethod = "clientIndisponible")
    @CircuitBreaker(name = "service-clients")
    @Bulkhead(name = "service-clients")
    public ClientDto getClientStrict(Long id) {
        ClientDto client = clientsClient.getClient(id);
        mettreEnCache("clients", id, client);
        return client;
    }

    @Retry(name = "service-abonnements", fallbackMethod = "abonnementIndisponible")
    @CircuitBreaker(name = "service-abonnements")
    @Bulkhead(name = "service-abonnements")
    public SubscriptionDto getSubscriptionStrict(Long id) {
        SubscriptionDto sub = abonnementsClient.getSubscription(id);
        mettreEnCache("abonnements", id, sub);
        return sub;
    }

    // ========== Strict fallbacks ==========

    private ClientDto clientIndisponible(Long id, RuntimeException ex) {
        if (!estIndisponible(ex)) { throw ex; }
        throw new ServiceExterneIndisponibleException(
                "Service-clients est momentanément indisponible. Réessayez dans quelques instants.");
    }

    private SubscriptionDto abonnementIndisponible(Long id, RuntimeException ex) {
        if (!estIndisponible(ex)) { throw ex; }
        throw new ServiceExterneIndisponibleException(
                "Service-abonnements est momentanément indisponible. Réessayez dans quelques instants.");
    }

    // ========== Tolerant fallbacks ==========

    private ClientDto clientDeRepli(Long id, RuntimeException ex) {
        if (!estIndisponible(ex)) { throw ex; }
        ClientDto enCache = lireCache("clients", id, ClientDto.class);
        log.info("Client {} loaded from cache or null", id);
        return enCache != null ? enCache : new ClientDto(id, null, null, null, null, null);
    }

    private SubscriptionDto abonnementDeRepli(Long id, RuntimeException ex) {
        if (!estIndisponible(ex)) { throw ex; }
        SubscriptionDto enCache = lireCache("abonnements", id, SubscriptionDto.class);
        log.info("Subscription {} loaded from cache or null", id);
        return enCache != null ? enCache : new SubscriptionDto(id, null, null, null);
    }

    private PlanDto planDeRepli(Long id, RuntimeException ex) {
        if (!estIndisponible(ex)) { throw ex; }
        PlanDto enCache = lireCache("plans", id, PlanDto.class);
        log.info("Plan {} loaded from cache or null", id);
        return enCache != null ? enCache : new PlanDto(id, null, null);
    }

    /** Covered outages: open circuit, Feign communication error (-1) or remote HTTP 503. Others (404...) are rethrown. */
    private boolean estIndisponible(RuntimeException cause) {
        return cause instanceof CallNotPermittedException
                || (cause instanceof FeignException feign
                && (feign.status() == -1 || feign.status() == 503));
    }

    // ========== Cache ==========

    private void mettreEnCache(String nomCache, Object cle, Object valeur) {
        Cache cache = cacheManager.getCache(nomCache);
        if (cache != null) {
            cache.put(cle, valeur);
        }
    }

    private <T> T lireCache(String nomCache, Object cle, Class<T> type) {
        Cache cache = cacheManager.getCache(nomCache);
        return cache != null ? cache.get(cle, type) : null;
    }
}