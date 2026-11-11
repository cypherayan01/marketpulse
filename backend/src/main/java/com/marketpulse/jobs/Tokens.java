package com.marketpulse.jobs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Shared-secret checks. Fail closed when no secret is configured; constant-time comparison otherwise. */
public final class Tokens {

    private Tokens() {
    }

    public static boolean matches(String expected, String provided) {
        if (expected == null || expected.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    /** Extracts the token from an {@code Authorization: Bearer <token>} header. */
    public static String bearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }
}
