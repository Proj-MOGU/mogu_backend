package com.mogu.auth.infrastructure;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mogu.auth.pii")
public record PiiProperties(
        Map<String, String> keys, String activeKeyVersion, boolean ephemeralKeyAllowed, String encryptionKeys) {
    public PiiProperties {
        keys = keys == null ? Map.of() : Map.copyOf(keys);
    }

    @Override
    public String toString() {
        return "PiiProperties[keys=***]";
    }
}
