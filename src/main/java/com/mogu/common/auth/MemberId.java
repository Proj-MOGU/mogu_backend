package com.mogu.common.auth;

import java.util.UUID;

public record MemberId(UUID value) {
    public MemberId {
        if (value == null) throw new IllegalArgumentException("memberId is required");
    }

    public static MemberId from(String value) {
        if (value == null
                || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("memberId must be a UUID");
        return new MemberId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
