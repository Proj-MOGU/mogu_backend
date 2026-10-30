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
    private final JdbcClient jdbc;
    private final Clock clock;
    private final HmacDiHasher mockHasher;
    private final SecureRandom random = new SecureRandom();

    public MockIdentityVerificationAdapter(
            JdbcClient jdbc,
            @Qualifier("authClock") Clock clock,
            @Value("${mogu.auth.mock-identity.secret:}") String secret) {
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
        if (registrationId == null
                || phoneNumber == null
                || !phoneNumber.matches("^01[016789][0-9]{7,8}$")
                || (outcome != null && !outcome.equals("VERIFIED") && !outcome.equals("FAILED")))
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.sql(
                        "INSERT INTO auth_mock_identity_verification(verification_id, registration_id, mock_di, outcome, verified_at) VALUES (:id, :registration, :di, :outcome, :now)")
                .param("id", id)
                .param("registration", registrationId)
                .param("di", mockHasher.hash(phoneNumber).value())
                .param("outcome", outcome == null ? "VERIFIED" : outcome)
                .param("now", clock.instant().atOffset(ZoneOffset.UTC))
                .update();
        return id;
    }

    @Override
    public VerifiedIdentity fetch(String verificationId) {
        if (verificationId == null || !verificationId.matches("[A-Za-z0-9_-]{32}")) throw invalid();
        return jdbc.sql(
                        "SELECT * FROM auth_mock_identity_verification WHERE verification_id = :id AND outcome = 'VERIFIED'")
                .param("id", verificationId)
                .query((rs, n) -> {
                    String di = rs.getString("mock_di");
                    if (di == null || !di.matches("[0-9a-f]{64}")) throw invalid();
                    return new VerifiedIdentity(
                            rs.getString("verification_id"),
                            rs.getObject("registration_id", UUID.class),
                            di,
                            rs.getTimestamp("verified_at").toInstant(),
                            IdentitySource.MOCK);
                })
                .optional()
                .orElseThrow(MockIdentityVerificationAdapter::invalid);
    }

    private static BusinessException invalid() {
        return new BusinessException(AuthErrorCode.INVALID_IDENTITY_VERIFICATION);
    }
}
