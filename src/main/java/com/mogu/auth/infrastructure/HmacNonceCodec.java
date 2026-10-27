package com.mogu.auth.infrastructure;

import com.mogu.auth.application.IssuedNonce;
import com.mogu.auth.application.NonceCodec;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.common.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacNonceCodec implements NonceCodec {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final SecretKeySpec key;
    private final Duration ttl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public HmacNonceCodec(byte[] secret, Duration ttl, Clock clock) {
        if (secret.length < 32) throw new IllegalArgumentException("Nonce key too short");
        this.key = new SecretKeySpec(secret, "HmacSHA256");
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public IssuedNonce issue() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        var expiry = clock.instant().plus(ttl).truncatedTo(ChronoUnit.SECONDS);
        String body = "n1." + ENCODER.encodeToString(bytes) + "." + expiry.getEpochSecond();
        return new IssuedNonce(body + "." + ENCODER.encodeToString(mac(body)), expiry);
    }

    @Override
    public Instant verifyAndGetExpiry(String nonce) {
        if (nonce == null
                || nonce.length() > 128
                || !nonce.matches("n1\\.[A-Za-z0-9_-]{22}\\.[1-9][0-9]{0,18}\\.[A-Za-z0-9_-]{43}")) throw invalid();
        try {
            var parts = nonce.split("\\.");
            byte[] rand = Base64.getUrlDecoder().decode(parts[1]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[3]);
            if (!ENCODER.encodeToString(rand).equals(parts[1])
                    || !ENCODER.encodeToString(signature).equals(parts[3])) throw invalid();
            if (!MessageDigest.isEqual(mac(nonce.substring(0, nonce.lastIndexOf('.'))), signature)) throw invalid();
            var expiry = Instant.ofEpochSecond(Long.parseLong(parts[2]));
            var now = clock.instant();
            if (expiry.isBefore(now) || expiry.isAfter(now.plus(ttl).plusSeconds(60))) throw invalid();
            return expiry;
        } catch (IllegalArgumentException | java.time.DateTimeException ignored) {
            throw invalid();
        }
    }

    private byte[] mac(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException ignored) {
            throw new IllegalStateException("HmacSHA256 unavailable");
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
    }
}
