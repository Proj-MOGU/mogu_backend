package com.mogu.auth.infrastructure;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

final class JwtKeyLoader {
    private JwtKeyLoader() {}

    static JwtSigningKey load(
            JwtProperties.SigningKey configuredKey, boolean ephemeral, Environment environment, String prefix) {
        if (environment.acceptsProfiles(Profiles.of("prod")) && ephemeral) {
            throw new IllegalStateException("" + prefix + ".ephemeral-key-allowed");
        }
        var configured = configuredKey;
        boolean hasPem = configured != null && present(configured.privateKeyPem());
        boolean hasId = configured != null && present(configured.keyId());
        boolean hasPublicPem = configured != null && present(configured.publicKeyPem());
        if (hasPem != hasId || hasPem != hasPublicPem) throw signingConfigurationError(prefix);
        try {
            if (hasPem) {
                var key = (ECPrivateKey) KeyFactory.getInstance("EC")
                        .generatePrivate(new PKCS8EncodedKeySpec(pemBytes(configured.privateKeyPem(), "PRIVATE KEY")));
                requireP256(key.getParams());
                var publicKey = (ECPublicKey) KeyFactory.getInstance("EC")
                        .generatePublic(new X509EncodedKeySpec(pemBytes(configured.publicKeyPem(), "PUBLIC KEY")));
                requireP256(publicKey.getParams());
                verifyKeyPair(key, publicKey);
                return new JwtSigningKey(new ECKey.Builder(Curve.P_256, publicKey)
                        .privateKey(key)
                        .keyID(configured.keyId())
                        .build());
            }
            if (!ephemeral) throw signingConfigurationError(prefix);
            LoggerFactory.getLogger(JwtKeyConfig.class).warn("임시 JWT 서명 키를 사용합니다. 재시작하면 기존 토큰은 무효가 됩니다.");
            return new JwtSigningKey(new ECKeyGenerator(Curve.P_256)
                    .keyID(UUID.randomUUID().toString())
                    .generate());
        } catch (Exception ignored) {
            throw signingConfigurationError(prefix);
        }
    }

    static JWKSet verificationKeys(
            JwtSigningKey signingKey, List<JwtProperties.VerificationKey> verificationKeys, String prefix) {
        List<JWK> keys = new ArrayList<>();
        keys.add(signingKey.publicJwk());
        Set<String> ids = new HashSet<>();
        ids.add(signingKey.keyId());
        for (var verification : verificationKeys) {
            try {
                if (!present(verification.keyId()) || !ids.add(verification.keyId()))
                    throw new IllegalArgumentException();
                var key = (ECPublicKey) KeyFactory.getInstance("EC")
                        .generatePublic(new X509EncodedKeySpec(pemBytes(verification.publicKeyPem(), "PUBLIC KEY")));
                requireP256(key.getParams());
                keys.add(new ECKey.Builder(Curve.P_256, key)
                        .keyID(verification.keyId())
                        .build());
            } catch (Exception ignored) {
                throw new IllegalStateException(prefix + ".verification-keys");
            }
        }
        return new JWKSet(keys);
    }

    private static void verifyKeyPair(ECPrivateKey privateKey, ECPublicKey publicKey) throws GeneralSecurityException {
        byte[] challenge = new byte[32];
        new SecureRandom().nextBytes(challenge);
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(challenge);
        byte[] signed = signature.sign();
        signature.initVerify(publicKey);
        signature.update(challenge);
        if (!signature.verify(signed)) throw new IllegalArgumentException();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static byte[] pemBytes(String pem, String label) {
        String start = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        String trimmed = pem.strip();
        if (!trimmed.startsWith(start) || !trimmed.endsWith(end)) throw new IllegalArgumentException();
        return Base64.getDecoder()
                .decode(trimmed.substring(start.length(), trimmed.length() - end.length())
                        .replaceAll("\\s", ""));
    }

    private static void requireP256(ECParameterSpec parameters) {
        if (!Curve.P_256.equals(Curve.forECParameterSpec(parameters))) throw new IllegalArgumentException();
    }

    private static IllegalStateException signingConfigurationError(String prefix) {
        return new IllegalStateException(prefix + ".signing-key.key-id, " + prefix + ".signing-key.private-key-pem, "
                + prefix + ".signing-key.public-key-pem, " + prefix + ".ephemeral-key-allowed");
    }
}
