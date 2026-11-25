package com.billflex.facturation.controller;

import com.billflex.facturation.dto.InvoiceDetailResponse;
import com.billflex.facturation.dto.InvoiceRequest;
import com.billflex.facturation.dto.InvoiceResponse;
import com.billflex.facturation.service.CreationResult;
import com.billflex.facturation.service.InvoiceService;
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
@RequestMapping("/invoices")
@RequiredArgsConstructor
@Validated
public class InvoiceController {

    private final InvoiceService service;

    @GetMapping
    public List<InvoiceResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public InvoiceResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody InvoiceRequest request) {
        CreationResult<InvoiceResponse> result = service.create(idempotencyKey, request);
        InvoiceResponse created = result.body();
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        ResponseEntity.BodyBuilder response = ResponseEntity.created(location);
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(created);
    }

    @PutMapping("/{id}")
    public InvoiceResponse update(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/emettre")
    public InvoiceResponse emettre(@PathVariable Long id) {
        return service.emettre(id);
    }

    @PatchMapping("/{id}/payer")
    public InvoiceResponse payer(@PathVariable Long id) {
        return service.payer(id);
    }

    @PatchMapping("/{id}/en-retard")
    public InvoiceResponse enRetard(@PathVariable Long id) {
        return service.marquerEnRetard(id);
    }

    @PatchMapping("/{id}/annuler")
    public InvoiceResponse annuler(@PathVariable Long id) {
        return service.annuler(id);
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<InvoiceDetailResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(service.getDetail(id));
    }
}
