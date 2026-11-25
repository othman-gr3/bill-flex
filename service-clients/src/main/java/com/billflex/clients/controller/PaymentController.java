package com.billflex.clients.controller;

import com.billflex.clients.dto.PaymentRequest;
import com.billflex.clients.dto.PaymentResponse;
import com.billflex.clients.service.CreationResult;
import com.billflex.clients.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final PaymentService service;

    @GetMapping
    public List<PaymentResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public PaymentResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        CreationResult<PaymentResponse> result = service.create(idempotencyKey, request);
        PaymentResponse created = result.body();
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        ResponseEntity.BodyBuilder response = ResponseEntity.created(location);
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(created);
    }

    @PatchMapping("/{id}/payer")
    public PaymentResponse payer(@PathVariable Long id) {
        return service.payer(id);
    }
}
