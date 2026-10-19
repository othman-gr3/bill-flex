package com.billflex.clients.exception;

/** Duplicate value (unique constraint) -> HTTP 409. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) { super(message); }
}
