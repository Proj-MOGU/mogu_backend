package com.mogu.auth.support;

import com.mogu.auth.domain.AuthAccount;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class AuthAccountFixtures {
    private AuthAccountFixtures() {}

    public static void insert(JdbcClient jdbc, AuthAccount account) {
        jdbc.sql("INSERT INTO auth_account(member_id,status,sanctioned_until,created_at,di_hash) VALUES"
                        + " (:id,:status,:until,:created,:di)")
                .param("id", account.memberId().value())
                .param("di", account.diHash().value())
                .param("status", account.status().name())
                .param(
                        "until",
                        account.sanctionedUntil() == null ? null : utc(account.sanctionedUntil()),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .param("created", utc(account.createdAt()))
                .update();
        for (var linked : account.socialIdentities())
            jdbc.sql("INSERT INTO auth_social_identity(provider,provider_subject,member_id,linked_at) VALUES"
                            + " (:provider,:subject,:member,:linked)")
                    .param("provider", linked.identity().provider().name())
                    .param("subject", linked.identity().subject())
                    .param("member", account.memberId().value())
                    .param("linked", utc(linked.linkedAt()))
                    .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
