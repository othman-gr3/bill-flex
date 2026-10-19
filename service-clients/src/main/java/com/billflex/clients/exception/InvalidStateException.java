package com.billflex.clients.exception;

/** Operation not allowed in the current state (e.g. invalid status transition) -> HTTP 409. */
public class InvalidStateException extends RuntimeException {
    public InvalidStateException(String message) { super(message); }
}
