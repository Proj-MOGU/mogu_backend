package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.domain.AccountStatus;
import com.mogu.auth.domain.AuthAccount;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.domain.LinkedSocialIdentity;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthAccountMapperTest {
    private final AuthAccountMapper mapper = new AuthAccountMapper(com.mogu.auth.support.PiiTestKeys.cipher());
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-27T00:00:00Z");

    @Test
    @DisplayName("계정 엔티티의 모든 필드와 연결 목록을 도메인으로 복원한다")
    void toDomainMapsEveryField() {
        // given
        for (var status : AccountStatus.values()) {
            var until = status == AccountStatus.ACTIVE ? null : now.plusSeconds(100);
            var entity = new AuthAccountJpaEntity(
                    id,
                    "a".repeat(64),
                    status,
                    until,
                    now,
                    List.of(new AuthSocialIdentityJpaEntity(AuthProvider.GOOGLE, "sub", id, now.plusSeconds(1))));
            // when
            var account = mapper.toDomain(entity);
            // then
            assertThat(account.memberId()).isEqualTo(new MemberId(id));
            assertThat(account.diHash().value()).isEqualTo("a".repeat(64));
            assertThat(account.status()).isEqualTo(status);
            assertThat(account.sanctionedUntil()).isEqualTo(until);
            assertThat(account.createdAt()).isEqualTo(now);
            assertThat(account.socialIdentities())
                    .containsExactly(new LinkedSocialIdentity(
                            new SocialIdentity(AuthProvider.GOOGLE, "sub"), now.plusSeconds(1)));
        }
    }

    @Test
    @DisplayName("계정 도메인의 모든 필드와 연결 목록을 엔티티로 변환한다")
    void toJpaEntityMapsEveryField() {
        // given
        for (var status : AccountStatus.values()) {
            var until = status == AccountStatus.ACTIVE ? null : now.plusSeconds(100);
            var account = AuthAccount.reconstitute(
                    new MemberId(id),
                    new com.mogu.auth.domain.DiHash("a".repeat(64)),
                    status,
                    until,
                    now,
                    List.of(new LinkedSocialIdentity(
                            new SocialIdentity(AuthProvider.KAKAO, "sub"), now.plusSeconds(1))));
            // when
            var entity = mapper.toJpaEntity(account);
            // then
            assertThat(entity.getMemberId()).isEqualTo(id);
            assertThat(entity.getDiHash()).isEqualTo("a".repeat(64));
            assertThat(entity.getStatus()).isEqualTo(status);
            assertThat(entity.getSanctionedUntil()).isEqualTo(until);
            assertThat(entity.getCreatedAt()).isEqualTo(now);
            assertThat(entity.getSocialIdentities()).hasSize(1);
            var linked = entity.getSocialIdentities().getFirst();
            assertThat(linked.getMemberId()).isEqualTo(id);
            assertThat(linked.getProvider()).isEqualTo(AuthProvider.KAKAO);
            assertThat(linked.getProviderSubject()).isEqualTo("sub");
            assertThat(linked.getLinkedAt()).isEqualTo(now.plusSeconds(1));
        }
    }

    @Test
    @DisplayName("개인정보 암호문과 인증 여부를 도메인 평문으로 각각 복원한다")
    void toDomainDecryptsPersonalInfo() {
        // given
        var cipher = com.mogu.auth.support.PiiTestKeys.cipher();
        var entity = new AuthAccountJpaEntity(
                id,
                "a".repeat(64),
                AccountStatus.ACTIVE,
                null,
                now,
                List.of(new AuthSocialIdentityJpaEntity(
                        AuthProvider.GOOGLE,
                        "sub",
                        id,
                        now,
                        cipher.encrypt("hong@gmail.com", "auth_social_identity.email_enc:GOOGLE:sub"),
                        true)),
                cipher.encrypt("홍길동", "auth_account.name_enc:" + id),
                cipher.encrypt("01012345678", "auth_account.phone_enc:" + id));
        // when
        var account = mapper.toDomain(entity);
        // then
        assertThat(account.name()).isEqualTo("홍길동");
        assertThat(account.phoneNumber()).isEqualTo("01012345678");
        assertThat(account.socialIdentities().getFirst().email()).isEqualTo("hong@gmail.com");
        assertThat(account.socialIdentities().getFirst().emailVerified()).isTrue();
    }

    @Test
    @DisplayName("도메인 개인정보를 지정한 소유자와 컬럼 AAD로 암호화한다")
    void toJpaEntityEncryptsPersonalInfo() {
        // given
        var cipher = com.mogu.auth.support.PiiTestKeys.cipher();
        var social = new SocialIdentity(AuthProvider.KAKAO, "sub");
        var account = AuthAccount.register(
                        new MemberId(id), new com.mogu.auth.domain.DiHash("a".repeat(64)), social, now)
                .updatePersonalInfo("홍길동", "01012345678")
                .updateSocialEmail(social, "hong@kakao.com", true);
        // when
        var entity = mapper.toJpaEntity(account);
        // then
        assertThat(cipher.decrypt(entity.getNameEnc(), "auth_account.name_enc:" + id))
                .isEqualTo("홍길동");
        assertThat(cipher.decrypt(entity.getPhoneEnc(), "auth_account.phone_enc:" + id))
                .isEqualTo("01012345678");
        var linked = entity.getSocialIdentities().getFirst();
        assertThat(cipher.decrypt(linked.getEmailEnc(), "auth_social_identity.email_enc:KAKAO:sub"))
                .isEqualTo("hong@kakao.com");
        assertThat(linked.isEmailVerified()).isTrue();
        assertThat(entity.getNameEnc()).startsWith("v1.");
        assertThat(entity.getPhoneEnc()).startsWith("v1.");
        assertThat(linked.getEmailEnc()).startsWith("v1.");
    }

    @Test
    @DisplayName("복호화 실패는 해당 필드만 생략하고 경고에는 필드명과 회원 ID만 남긴다")
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void redactsDecryptionFailure(org.springframework.boot.test.system.CapturedOutput output) {
        // given
        var cipher = com.mogu.auth.support.PiiTestKeys.cipher();
        String broken = "lost-key-sensitive-value";
        var entity = new AuthAccountJpaEntity(
                id,
                "a".repeat(64),
                AccountStatus.ACTIVE,
                null,
                now,
                List.of(new AuthSocialIdentityJpaEntity(AuthProvider.GOOGLE, "sub", id, now, broken, true)),
                broken,
                cipher.encrypt("01012345678", "auth_account.phone_enc:" + id));
        // when
        var account = mapper.toDomain(entity);
        // then
        assertThat(account.name()).isNull();
        assertThat(account.phoneNumber()).isEqualTo("01012345678");
        assertThat(account.socialIdentities().getFirst().email()).isNull();
        assertThat(account.socialIdentities().getFirst().emailVerified()).isFalse();
        assertThat(output.getAll())
                .contains("name_enc", "email_enc", id.toString())
                .doesNotContain(broken, "01012345678");
        assertThat(output.getAll()
                        .lines()
                        .filter(line -> line.contains("PII decryption failed"))
                        .count())
                .isEqualTo(2);
    }
}
