package com.mogu.auth.infrastructure;

import com.mogu.auth.application.IdentitySource;
import com.mogu.auth.application.IdentityVerificationAdapter;
import com.mogu.auth.application.MockIdentityVerificationCreator;
import com.mogu.auth.application.VerifiedIdentity;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.common.error.BusinessException;
import com.mogu.common.error.CommonErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"local", "test"})
public class MockIdentityVerificationAdapter implements IdentityVerificationAdapter, MockIdentityVerificationCreator {
    private final com.mogu.auth.application.PiiCipher cipher;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final HmacDiHasher mockHasher;
    private final SecureRandom random = new SecureRandom();

    public MockIdentityVerificationAdapter(
            JdbcClient jdbc,
            @Qualifier("authClock") Clock clock,
            @Value("${mogu.auth.mock-identity.secret:}") String secret,
            com.mogu.auth.application.PiiCipher cipher) {
        this.cipher = cipher;
        this.jdbc = jdbc;
        this.clock = clock;
        byte[] key;
        if (secret.isBlank()) {
            key = new byte[32];
            random.nextBytes(key);
        } else {
            try {
                key = Base64.getDecoder().decode(secret);
            } catch (IllegalArgumentException ignored) {
                throw new IllegalStateException("mogu.auth.mock-identity.secret");
            }
            if (key.length < 32) throw new IllegalStateException("mogu.auth.mock-identity.secret");
        }
        mockHasher = new HmacDiHasher(key);
    }

    @Override
    @Transactional
    public String create(UUID registrationId, String phoneNumber, String outcome) {
        phoneNumber = phoneNumber == null ? null : phoneNumber.replace("-", "");
        if (registrationId == null
                || phoneNumber == null
                || !phoneNumber.matches("^01[016789][0-9]{7,8}$")
                || (outcome != null && !outcome.equals("VERIFIED") && !outcome.equals("FAILED")))
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.sql("INSERT INTO auth_mock_identity_verification(verification_id, registration_id, mock_di,"
                        + " outcome, verified_at, phone_enc, name_enc) VALUES (:id, :registration, :di, :outcome,"
                        + " :now, :phone, :name)")
                .param("phone", cipher.encrypt(phoneNumber, "auth_mock_identity_verification.phone_enc:" + id))
                .param("name", cipher.encrypt("테스트사용자", "auth_mock_identity_verification.name_enc:" + id))
                .param("id", id)
                .param("registration", registrationId)
                .param("di", mockHasher.hash(phoneNumber).value())
                .param("outcome", outcome == null ? "VERIFIED" : outcome)
                .param("now", clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC))
                .update();
        return id;
    }

    @Override
    public VerifiedIdentity fetch(String verificationId) {
        if (verificationId == null || !verificationId.matches("[A-Za-z0-9_-]{32}")) throw invalid();
        return jdbc.sql("SELECT * FROM auth_mock_identity_verification WHERE verification_id = :id AND outcome ="
                        + " 'VERIFIED'")
                .param("id", verificationId)
                .query((rs, n) -> {
                    String di = rs.getString("mock_di");
                    if (di == null || !di.matches("[0-9a-f]{64}")) throw invalid();
                    return new VerifiedIdentity(
                            rs.getString("verification_id"),
                            rs.getObject("registration_id", UUID.class),
                            di,
                            rs.getTimestamp("verified_at").toInstant(),
                            IdentitySource.MOCK,
                            decrypt(
                                    rs.getString("name_enc"),
                                    "auth_mock_identity_verification.name_enc:" + verificationId),
                            decrypt(
                                    rs.getString("phone_enc"),
                                    "auth_mock_identity_verification.phone_enc:" + verificationId));
                })
                .optional()
                .orElseThrow(MockIdentityVerificationAdapter::invalid);
    }

    private String decrypt(String value, String context) {
        if (value == null) throw invalid();
        try {
            return cipher.decrypt(value, context);
        } catch (IllegalStateException ignored) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(AuthErrorCode.INVALID_IDENTITY_VERIFICATION);
    }
}
