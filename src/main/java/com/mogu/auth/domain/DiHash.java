package com.mogu.auth.domain;

public record DiHash(String value) {
    public DiHash {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid DI hash");
    }

    @Override
    public String toString() {
        return value.substring(0, 8) + "…";
    }
}
