package com.mogu.auth.application;

import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.RefreshTokenRepository;
import com.mogu.auth.domain.SanctionedDiRepository;
import com.mogu.common.event.MemberSanctionLifted;
import com.mogu.common.event.MemberSanctioned;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SanctionEventHandler {
    private static final Logger log = LoggerFactory.getLogger(SanctionEventHandler.class);
    private final AuthAccountRepository accounts;
    private final SanctionedDiRepository sanctions;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public SanctionEventHandler(
            AuthAccountRepository accounts,
            SanctionedDiRepository sanctions,
            RefreshTokenRepository refreshTokens,
            @Qualifier("authClock") Clock clock) {
        this.accounts = accounts;
        this.sanctions = sanctions;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void onSanctioned(MemberSanctioned event) {
        var found = accounts.findByMemberIdForUpdate(event.memberId());
        if (found.isEmpty()) {
            log.warn("제재 대상 인증 계정이 없습니다: {}", event.memberId());
            return;
        }
        if (found.get().isWithdrawn()) {
            var account = found.get();
            if (account.diHash() != null)
                sanctions.upsert(
                        account.diHash(),
                        account.memberId(),
                        event.until(),
                        clock.instant().truncatedTo(ChronoUnit.MICROS));
            else log.warn("탈퇴 계정의 제재 식별 정보가 없습니다 memberId={}", account.memberId());
            return;
        }
        var account = found.get().sanction(event.until());
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        accounts.save(account);
        sanctions.upsert(account.diHash(), account.memberId(), event.until(), now);
        refreshTokens.revokeAllActiveByMember(account.memberId(), now);
    }

    @EventListener
    @Transactional
    public void onLifted(MemberSanctionLifted event) {
        var found = accounts.findByMemberIdForUpdate(event.memberId());
        if (found.isEmpty()) {
            log.warn("제재 해제 대상 인증 계정이 없습니다: {}", event.memberId());
            return;
        }
        if (found.get().isWithdrawn()) {
            if (found.get().diHash() != null) sanctions.delete(found.get().diHash());
            return;
        }
        var account = found.get().lift();
        accounts.save(account);
        sanctions.delete(account.diHash());
    }
}
