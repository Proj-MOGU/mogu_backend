package com.mogu.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class RefreshTokenHasher {
    private RefreshTokenHasher() {}

    static boolean isValidFormat(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{43}");
    }

    static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ignored) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
