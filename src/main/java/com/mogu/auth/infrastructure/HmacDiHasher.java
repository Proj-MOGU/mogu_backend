package com.mogu.auth.infrastructure;

import com.mogu.auth.application.DiHasher;
import com.mogu.auth.domain.DiHash;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacDiHasher implements DiHasher {
    private final SecretKeySpec key;

    public HmacDiHasher(byte[] pepper) {
        if (pepper == null || pepper.length < 32) throw new IllegalArgumentException("DI pepper too short");
        key = new SecretKeySpec(pepper, "HmacSHA256");
    }

    @Override
    public DiHash hash(String di) {
        if (di == null || di.isBlank()) throw new IllegalArgumentException("Invalid DI");
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return new DiHash(HexFormat.of().formatHex(mac.doFinal(di.getBytes(StandardCharsets.UTF_8))));
        } catch (GeneralSecurityException ignored) {
            throw new IllegalStateException("HmacSHA256 unavailable");
        }
    }
}
