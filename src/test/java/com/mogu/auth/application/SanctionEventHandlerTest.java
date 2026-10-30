package com.mogu.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.RefreshTokenRepository;
import com.mogu.auth.domain.SanctionedDiRepository;
import com.mogu.common.auth.MemberId;
import com.mogu.common.event.MemberSanctionLifted;
import com.mogu.common.event.MemberSanctioned;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SanctionEventHandlerTest {
    @Test
    @DisplayName("없는 회원의 제재와 해제 이벤트는 예외 없이 무시한다")
    void ignoresMissingMember() {
        // given
        var accounts = mock(AuthAccountRepository.class);
        var sanctions = mock(SanctionedDiRepository.class);
        var refresh = mock(RefreshTokenRepository.class);
        var handler = new SanctionEventHandler(accounts, sanctions, refresh, Clock.systemUTC());
        var member = new MemberId(UUID.randomUUID());
        // when / then
        assertThatCode(() -> handler.onSanctioned(new MemberSanctioned(member, null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> handler.onLifted(new MemberSanctionLifted(member))).doesNotThrowAnyException();
        verifyNoInteractions(sanctions, refresh);
    }
}
