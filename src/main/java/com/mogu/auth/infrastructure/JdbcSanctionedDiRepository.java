package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.DiHash;
import com.mogu.auth.domain.SanctionedDiRepository;
import com.mogu.common.auth.MemberId;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSanctionedDiRepository implements SanctionedDiRepository {
    private final JdbcClient jdbc;

    public JdbcSanctionedDiRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(DiHash diHash, MemberId memberId, Instant until, Instant now) {
        jdbc.sql(
                        "INSERT INTO auth_sanctioned_di(di_hash, member_id, sanctioned_until, sanctioned_at) VALUES (:di, :member, :until, :now) ON CONFLICT (di_hash) DO UPDATE SET member_id = EXCLUDED.member_id, sanctioned_until = EXCLUDED.sanctioned_until, sanctioned_at = EXCLUDED.sanctioned_at")
                .param("di", diHash.value())
                .param("member", memberId.value())
                .param("until", until == null ? null : until.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
    }

    @Override
    public void delete(DiHash diHash) {
        jdbc.sql("DELETE FROM auth_sanctioned_di WHERE di_hash = :di")
                .param("di", diHash.value())
                .update();
    }

    @Override
    public boolean isSanctioned(DiHash diHash, Instant now) {
        return jdbc.sql(
                        "SELECT EXISTS(SELECT 1 FROM auth_sanctioned_di WHERE di_hash = :di AND (sanctioned_until IS NULL OR sanctioned_until > :now))")
                .param("di", diHash.value())
                .param("now", now.atOffset(ZoneOffset.UTC))
                .query(Boolean.class)
                .single();
    }
}
