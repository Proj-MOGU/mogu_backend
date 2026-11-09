package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.DiHash;
import com.mogu.auth.domain.RegistrationLock;
import com.mogu.auth.domain.SocialIdentity;
import java.util.stream.Stream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresRegistrationLock implements RegistrationLock {
    private final JdbcClient jdbc;

    public PostgresRegistrationLock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(DiHash diHash, SocialIdentity identity) {
        Stream.of("di:" + diHash.value(), "social:" + identity.provider() + ":" + identity.subject())
                .sorted()
                .forEach(key -> jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                        .param("key", key)
                        .query((rs, n) -> true)
                        .single());
    }
}
