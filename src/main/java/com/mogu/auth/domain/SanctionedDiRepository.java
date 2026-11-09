package com.mogu.auth.domain;

import com.mogu.common.auth.MemberId;
import java.time.Instant;

public interface SanctionedDiRepository {
    void upsert(DiHash diHash, MemberId memberId, Instant until, Instant now);

    void delete(DiHash diHash);

    boolean isSanctioned(DiHash diHash, Instant now);
}
