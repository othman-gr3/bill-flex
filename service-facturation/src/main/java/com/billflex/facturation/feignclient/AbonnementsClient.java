package com.billflex.facturation.feignclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "service-abonnements")
public interface AbonnementsClient {

    @GetMapping("/subscriptions/{id}")
    SubscriptionDto getSubscription(@PathVariable("id") Long id);

    @GetMapping("/plans/{id}")
    PlanDto getPlan(@PathVariable("id") Long id);
}