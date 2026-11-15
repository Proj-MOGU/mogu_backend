package com.mogu.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.mogu.auth.api.WithdrawalController;
import com.mogu.auth.api.WithdrawalRequest;
import com.mogu.auth.application.*;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.infrastructure.LoginMemberArgumentResolver;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import com.mogu.common.error.GlobalExceptionHandler;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class WithdrawalSecretRedactionTest {
    @Test
    @DisplayName("탈퇴 성공과 재인증 및 입력 실패의 DEBUG 로그에 ID Token과 subject 및 이메일이 없다")
    void neverLogsWithdrawalSecrets(CapturedOutput output) throws Exception {
        // given: DB와 공급자 대역만 사용하고 실제 컨트롤러·퍼사드·오류 처리를 실행한다.
        var id = new MemberId(UUID.randomUUID());
        var accounts = mock(WithdrawalAccountQuery.class);
        var verification = mock(SocialTokenVerificationService.class);
        var completion = mock(WithdrawalCompletionService.class);
        String valid = "withdrawal-secret-valid-token";
        String invalid = "withdrawal-secret-invalid-token";
        String subject = "withdrawal-secret-subject";
        String email = "withdrawal-secret@example.com";
        var identity = new VerifiedSocialIdentity(AuthProvider.KAKAO, subject, "nonce", email, true);
        when(verification.verifyToken(AuthProvider.KAKAO, valid)).thenReturn(identity);
        when(verification.verifyToken(AuthProvider.KAKAO, invalid))
                .thenThrow(new BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN));
        when(completion.withdraw(id, identity)).thenReturn(new WithdrawalOutcome.Withdrawn());
        var web = (Logger) LoggerFactory.getLogger("org.springframework.web");
        var auth = (Logger) LoggerFactory.getLogger("com.mogu.auth");
        Level webLevel = web.getLevel();
        Level authLevel = auth.getLevel();
        web.setLevel(Level.DEBUG);
        auth.setLevel(Level.DEBUG);
        try {
            var mvc = MockMvcBuilders.standaloneSetup(
                            new WithdrawalController(new WithdrawalService(accounts, verification, completion)))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(new LoginMemberArgumentResolver(() -> Optional.of(id)))
                    .build();
            LoggerFactory.getLogger(getClass()).debug("withdrawal-secret-capture-control");
            assertThat(output.getAll()).contains("withdrawal-secret-capture-control");
            // when / then
            for (String token : new String[] {valid, invalid, " "}) {
                mvc.perform(post("/api/auth/withdrawal")
                                .contentType("application/json")
                                .content(new JsonMapper().writeValueAsString(new WithdrawalRequest("kakao", token))))
                        .andExpect(status().is(token.equals(valid) ? 204 : token.equals(invalid) ? 403 : 400));
            }
            assertThat(output.getAll()).doesNotContain(valid, invalid, subject, email);
            assertThat(new WithdrawalRequest("kakao", valid).toString())
                    .contains("***")
                    .doesNotContain(valid);
        } finally {
            web.setLevel(webLevel);
            auth.setLevel(authLevel);
        }
    }
}
