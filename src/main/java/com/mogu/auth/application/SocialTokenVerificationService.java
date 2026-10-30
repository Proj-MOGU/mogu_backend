package com.mogu.auth.application;

import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.domain.UsedNonceRepository;
import com.mogu.common.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialTokenVerificationService {
    private final Map<AuthProvider, SocialLoginAdapter> adapters;
    private final NonceCodec codec;
    private final UsedNonceRepository repository;
    private final Clock clock;

    public SocialTokenVerificationService(
            List<SocialLoginAdapter> adapters,
            NonceCodec codec,
            UsedNonceRepository repository,
            @Qualifier("authClock") Clock clock) {
        var registered = new EnumMap<AuthProvider, SocialLoginAdapter>(AuthProvider.class);
        for (var adapter : adapters)
            if (registered.putIfAbsent(adapter.provider(), adapter) != null)
                throw new IllegalStateException("Duplicate social provider");
        this.adapters = Map.copyOf(registered);
        this.codec = codec;
        this.repository = repository;
        this.clock = clock;
    }

    public VerifiedSocialIdentity verifyToken(AuthProvider provider, String idToken) {
        var adapter = provider == null ? null : adapters.get(provider);
        if (adapter == null) throw new BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        var identity = adapter.verify(idToken);
        var expiry = codec.verifyAndGetExpiry(identity.nonce());
        if (expiry.isBefore(clock.instant())
                || identity.subject() == null
                || identity.subject().isBlank()
                || identity.subject().length() > 255) throw new BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        return identity;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consumeNonce(VerifiedSocialIdentity identity) {
        var expiry = codec.verifyAndGetExpiry(identity.nonce());
        if (expiry.isBefore(clock.instant()) || !repository.markUsed(sha256Hex(identity.nonce()), expiry))
            throw new BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ignored) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
