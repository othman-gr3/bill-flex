package com.billflex.facturation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

final class CanonicalRequestHasher {

    private CanonicalRequestHasher() {
    }

    static String sha256(Object request, ObjectMapper objectMapper) {
        try {
            JsonNode canonical = canonicalize(objectMapper.valueToTree(request), objectMapper);
            byte[] body = objectMapper.writeValueAsBytes(canonical);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Impossible de calculer l'empreinte de la requête", ex);
        }
    }

    private static JsonNode canonicalize(JsonNode node, ObjectMapper objectMapper) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((name, value) -> sorted.set(name, canonicalize(value, objectMapper)));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(value -> sorted.add(canonicalize(value, objectMapper)));
            return sorted;
        }
        return node;
    }
}
