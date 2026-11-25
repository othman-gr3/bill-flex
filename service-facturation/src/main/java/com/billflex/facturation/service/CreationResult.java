package com.billflex.facturation.service;

public record CreationResult<T>(T body, boolean replayed) {
}
