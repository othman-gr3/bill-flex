package com.billflex.abonnements.controller;

import com.billflex.abonnements.dto.SubscriptionRequest;
import com.billflex.abonnements.dto.SubscriptionResponse;
import com.billflex.abonnements.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService service;

    @GetMapping
    public List<SubscriptionResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public SubscriptionResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(@Valid @RequestBody SubscriptionRequest request) {
        SubscriptionResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public SubscriptionResponse update(@PathVariable Long id, @Valid @RequestBody SubscriptionRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/resilier")
    public SubscriptionResponse resilier(@PathVariable Long id) {
        return service.resilier(id);
    }
}
