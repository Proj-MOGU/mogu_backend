package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.UsedIdentityVerificationRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUsedIdentityVerificationRepository implements UsedIdentityVerificationRepository {
    private final JdbcClient jdbc;

    public JdbcUsedIdentityVerificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markUsed(String verificationId, Instant now) {
        return jdbc.sql(
                                "INSERT INTO auth_used_identity_verification(verification_id, used_at) VALUES (:id, :now) ON CONFLICT (verification_id) DO NOTHING")
                        .param("id", verificationId)
                        .param("now", now.atOffset(ZoneOffset.UTC))
                        .update()
                == 1;
    }
}
