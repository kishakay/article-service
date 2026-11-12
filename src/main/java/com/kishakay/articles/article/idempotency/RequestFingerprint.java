package com.kishakay.articles.article.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kishakay.articles.article.dto.CreateArticleRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic SHA-256 fingerprint of a validated create payload.
 * Field order is fixed ({@code title}, then {@code body}) so equivalent requests hash equally.
 */
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    public static String forCreateArticle(CreateArticleRequest request, ObjectMapper objectMapper) {
        Map<String, String> canonical = new LinkedHashMap<>();
        canonical.put("title", request.title());
        canonical.put("body", request.body());
        try {
            byte[] json = objectMapper.writeValueAsBytes(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialise create payload for fingerprint", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
