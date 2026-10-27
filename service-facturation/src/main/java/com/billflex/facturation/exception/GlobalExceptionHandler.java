package com.billflex.facturation.exception;

import com.billflex.facturation.dto.ApiError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import feign.FeignException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler({DuplicateResourceException.class, InvalidStateException.class})
    public ResponseEntity<ApiError> conflict(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT,
                "Opération impossible : contrainte d'intégrité violée (doublon ou ressource référencée)", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> details.put(e.getField(), e.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Erreur de validation", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST,
                "Corps de requête invalide (JSON mal formé, date ou valeur d'énumération incorrecte)", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Paramètre invalide : " + ex.getName(), null);
    }
    @ExceptionHandler(FeignException.NotFound.class)
    public ResponseEntity<ApiError> externalNotFound(FeignException.NotFound ex) {
        return build(HttpStatus.NOT_FOUND, "Ressource externe introuvable", null);
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> feign(FeignException ex) {
        if (ex.status() == -1) {
            return build(HttpStatus.SERVICE_UNAVAILABLE, "Échec de communication avec un service", null);
        }
        if (ex.status() == 503) {
            return build(HttpStatus.SERVICE_UNAVAILABLE, "Service ou instance indisponible", null);
        }
        return build(HttpStatus.BAD_GATEWAY, "Erreur HTTP du service distant : " + ex.status(), null);
    }
    private ResponseEntity<ApiError> build(HttpStatus status, String message, Map<String, String> details) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, details));
    }
}
