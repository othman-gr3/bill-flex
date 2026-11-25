package com.billflex.clients.service;

public record CreationResult<T>(T body, boolean replayed) {
}
