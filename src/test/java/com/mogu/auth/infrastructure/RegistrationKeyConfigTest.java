package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.ECKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class RegistrationKeyConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JwtKeyConfig.class, RegistrationKeyConfig.class)
            .withPropertyValues(
                    "mogu.auth.jwt.issuer=mogu",
                    "mogu.auth.jwt.audience=mogu-api",
                    "mogu.auth.jwt.access-token-ttl=15m",
                    "mogu.auth.jwt.clock-skew=60s",
                    "mogu.auth.jwt.ephemeral-key-allowed=true")
            .withPropertyValues(
                    "mogu.auth.registration-jwt.issuer=mogu",
                    "mogu.auth.registration-jwt.audience=mogu-registration",
                    "mogu.auth.registration-jwt.ttl=15m",
                    "mogu.auth.registration-jwt.clock-skew=60s");

    @Test
    @DisplayName("키가 없고 임시 키가 금지되면 기동 실패한다")
    void missingSigningKeyFails() {
        // given / when / then
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("운영 프로파일에서는 임시 키 허용 설정을 거부한다")
    void productionRejectsEphemeralKeys() throws Exception {
        // given
        var access = ecKeyPair("secp256r1");
        // when / then
        runner.withPropertyValues(
                        "mogu.auth.jwt.ephemeral-key-allowed=false",
                        "mogu.auth.jwt.signing-key.key-id=access",
                        "mogu.auth.jwt.signing-key.private-key-pem="
                                + JwtAccessTokenTest.pem(
                                        "PRIVATE KEY", access.getPrivate().getEncoded()),
                        "mogu.auth.jwt.signing-key.public-key-pem="
                                + JwtAccessTokenTest.pem(
                                        "PUBLIC KEY", access.getPublic().getEncoded()),
                        "spring.profiles.active=local,prod",
                        "mogu.auth.registration-jwt.ephemeral-key-allowed=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("mogu.auth.registration-jwt.ephemeral-key-allowed"));
    }

    @Test
    @DisplayName("로컬 프로파일에서 임시 키를 허용하면 인코더와 디코더를 등록한다")
    void localAllowsEphemeralKeys() {
        // given / when / then
        runner.withPropertyValues(
                        "spring.profiles.active=local", "mogu.auth.registration-jwt.ephemeral-key-allowed=true")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasBean("registrationJwtEncoder")
                        .hasBean("registrationJwtDecoder"));
    }

    @Test
    @DisplayName("PEM 개인키로 발급하고 설정한 키 ID와 공개키로 검증한다")
    void configuredPemIsUsed() throws Exception {
        // given
        var key = ecKeyPair("secp256r1");
        var pem = JwtAccessTokenTest.pem("PRIVATE KEY", key.getPrivate().getEncoded());
        var publicPem = JwtAccessTokenTest.pem("PUBLIC KEY", key.getPublic().getEncoded());
        // when / then
        runner.withPropertyValues(
                        "mogu.auth.registration-jwt.signing-key.key-id=configured",
                        "mogu.auth.registration-jwt.signing-key.private-key-pem=" + pem,
                        "mogu.auth.registration-jwt.signing-key.public-key-pem=" + publicPem)
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(ECKey.class);
                    var signingKey = context.getBean("registrationSigningKey", JwtSigningKey.class);
                    assertThat(signingKey.publicJwk().isPrivate()).isFalse();
                    assertThat(signingKey.toString()).isEqualTo("JwtSigningKey[keyId=configured]");
                    var properties = context.getBean(RegistrationJwtProperties.class);
                    var clock = context.getBean(Clock.class);
                    var issuer = new JwtRegistrationTokenIssuer(
                            context.getBean("registrationJwtEncoder", JwtEncoder.class),
                            properties,
                            clock,
                            context.getBean("registrationSigningKey", JwtSigningKey.class));
                    var token = issuer.issue(new com.mogu.auth.domain.SocialIdentity(
                            com.mogu.auth.domain.AuthProvider.KAKAO, "subject"));
                    assertThat(context.getBean("registrationJwtDecoder", JwtDecoder.class)
                                    .decode(token.value())
                                    .getHeaders())
                            .containsEntry("kid", "configured");
                    var decoded = context.getBean("registrationJwtDecoder", JwtDecoder.class)
                            .decode(token.value());
                    assertThat(decoded.getHeaders()).containsEntry("typ", "registration+jwt");
                    assertThat(decoded.getAudience()).containsExactly("mogu-registration");
                    assertThat(decoded.getClaimAsString("provider")).isEqualTo("KAKAO");
                    assertThat(decoded.getClaimAsString("provider_sub")).isEqualTo("subject");
                    assertThat(properties.toString())
                            .doesNotContain(pem, publicPem)
                            .contains("***");
                });
    }

    @Test
    @DisplayName("RSA 개인키를 거부한다")
    void rsaPemIsRejected() throws Exception {
        // given
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var key = generator.generateKeyPair();
        var pem = JwtAccessTokenTest.pem("PRIVATE KEY", key.getPrivate().getEncoded());
        var publicPem = JwtAccessTokenTest.pem("PUBLIC KEY", key.getPublic().getEncoded());
        // when / then
        runner.withPropertyValues(
                        "mogu.auth.registration-jwt.signing-key.key-id=rsa",
                        "mogu.auth.registration-jwt.signing-key.private-key-pem=" + pem,
                        "mogu.auth.registration-jwt.signing-key.public-key-pem=" + publicPem)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("P-384 개인키를 거부한다")
    void p384PemIsRejected() throws Exception {
        // given
        var key = ecKeyPair("secp384r1");
        var pem = JwtAccessTokenTest.pem("PRIVATE KEY", key.getPrivate().getEncoded());
        var publicPem = JwtAccessTokenTest.pem("PUBLIC KEY", key.getPublic().getEncoded());
        // when / then
        runner.withPropertyValues(
                        "mogu.auth.registration-jwt.signing-key.key-id=p384",
                        "mogu.auth.registration-jwt.signing-key.private-key-pem=" + pem,
                        "mogu.auth.registration-jwt.signing-key.public-key-pem=" + publicPem)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("서명 키와 검증 키의 ID 중복을 거부한다")
    void duplicateKeyIdIsRejected() throws Exception {
        // given
        var key = ecKeyPair("secp256r1");
        var privatePem = JwtAccessTokenTest.pem("PRIVATE KEY", key.getPrivate().getEncoded());
        var publicPem = JwtAccessTokenTest.pem("PUBLIC KEY", key.getPublic().getEncoded());
        // when / then
        runner.withPropertyValues(
                        "mogu.auth.registration-jwt.signing-key.key-id=same",
                        "mogu.auth.registration-jwt.signing-key.private-key-pem=" + privatePem,
                        "mogu.auth.registration-jwt.signing-key.public-key-pem=" + publicPem,
                        "mogu.auth.registration-jwt.verification-keys[0].key-id=same",
                        "mogu.auth.registration-jwt.verification-keys[0].public-key-pem=" + publicPem)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("불완전한 서명 키 설정은 임시 키로 대체하지 않는다")
    void partialConfigurationFailsClosed() {
        // given / when / then
        runner.withPropertyValues(
                        "mogu.auth.registration-jwt.signing-key.key-id=partial",
                        "mogu.auth.registration-jwt.ephemeral-key-allowed=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("개인키와 키 ID만 있고 공개키가 없으면 기동 실패한다")
    void missingPublicKeyFails() throws Exception {
        // given
        var key = ecKeyPair("secp256r1");
        var privatePem = JwtAccessTokenTest.pem("PRIVATE KEY", key.getPrivate().getEncoded());
        // when / then
        runner.withPropertyValues(
                        "mogu.auth.registration-jwt.signing-key.key-id=missing-public",
                        "mogu.auth.registration-jwt.signing-key.private-key-pem=" + privatePem,
                        "mogu.auth.registration-jwt.ephemeral-key-allowed=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("다른 키 쌍의 공개키를 설정하면 기동 실패한다")
    void mismatchedPublicKeyFails() throws Exception {
        // given
        var key = ecKeyPair("secp256r1");
        var other = ecKeyPair("secp256r1");
        // when / then
        assertSigningKeyRejected(key, other);
    }

    @Test
    @DisplayName("P-384 공개키를 설정하면 기동 실패한다")
    void p384PublicKeyFails() throws Exception {
        // given
        var key = ecKeyPair("secp256r1");
        var other = ecKeyPair("secp384r1");
        // when / then
        assertSigningKeyRejected(key, other);
    }

    private void assertSigningKeyRejected(KeyPair privatePair, KeyPair publicPair) {
        var privatePem =
                JwtAccessTokenTest.pem("PRIVATE KEY", privatePair.getPrivate().getEncoded());
        var publicPem =
                JwtAccessTokenTest.pem("PUBLIC KEY", publicPair.getPublic().getEncoded());
        runner.withPropertyValues(
                        "mogu.auth.registration-jwt.signing-key.key-id=invalid",
                        "mogu.auth.registration-jwt.signing-key.private-key-pem=" + privatePem,
                        "mogu.auth.registration-jwt.signing-key.public-key-pem=" + publicPem)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Access Token과 가입 대기 서명 키 ID가 같으면 기동 실패한다")
    void sharedKeyIdFails() throws Exception {
        // given
        var access = ecKeyPair("secp256r1");
        var registration = ecKeyPair("secp256r1");
        // when / then
        runner.withPropertyValues(
                        "mogu.auth.jwt.signing-key.key-id=same",
                        "mogu.auth.jwt.signing-key.private-key-pem="
                                + JwtAccessTokenTest.pem(
                                        "PRIVATE KEY", access.getPrivate().getEncoded()),
                        "mogu.auth.jwt.signing-key.public-key-pem="
                                + JwtAccessTokenTest.pem(
                                        "PUBLIC KEY", access.getPublic().getEncoded()),
                        "mogu.auth.registration-jwt.signing-key.key-id=same",
                        "mogu.auth.registration-jwt.signing-key.private-key-pem="
                                + JwtAccessTokenTest.pem(
                                        "PRIVATE KEY", registration.getPrivate().getEncoded()),
                        "mogu.auth.registration-jwt.signing-key.public-key-pem="
                                + JwtAccessTokenTest.pem(
                                        "PUBLIC KEY", registration.getPublic().getEncoded()))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("키 ID가 달라도 Access Token 키 쌍을 재사용하면 기동 실패한다")
    void sharedKeyPairFails() throws Exception {
        // given
        var key = ecKeyPair("secp256r1");
        var privatePem = JwtAccessTokenTest.pem("PRIVATE KEY", key.getPrivate().getEncoded());
        var publicPem = JwtAccessTokenTest.pem("PUBLIC KEY", key.getPublic().getEncoded());
        // when / then
        runner.withPropertyValues(
                        "mogu.auth.jwt.signing-key.key-id=access",
                        "mogu.auth.jwt.signing-key.private-key-pem=" + privatePem,
                        "mogu.auth.jwt.signing-key.public-key-pem=" + publicPem,
                        "mogu.auth.registration-jwt.signing-key.key-id=registration",
                        "mogu.auth.registration-jwt.signing-key.private-key-pem=" + privatePem,
                        "mogu.auth.registration-jwt.signing-key.public-key-pem=" + publicPem)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("Distinct access and registration signing keys required"));
    }

    @Test
    @DisplayName("검증 키 설정의 문자열에서도 PEM 원문을 숨긴다")
    void verificationKeyRedactsPem() {
        // given
        var key = new JwtProperties.VerificationKey("old", "public-key-material");
        // when / then
        assertThat(key.toString()).contains("***").doesNotContain("public-key-material");
    }

    private static KeyPair ecKeyPair(String curve) throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(curve));
        return generator.generateKeyPair();
    }
}
