package com.mogu.auth.infrastructure;

import com.mogu.auth.application.PiiCipher;
import com.mogu.auth.domain.AuthAccount;
import com.mogu.auth.domain.DiHash;
import com.mogu.auth.domain.LinkedSocialIdentity;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.common.auth.MemberId;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuthAccountMapper {
    private final PiiCipher cipher;

    public AuthAccountMapper(PiiCipher cipher) {
        this.cipher = cipher;
    }

    public AuthAccount toDomain(AuthAccountJpaEntity entity) {
        var id = entity.getMemberId();
        return AuthAccount.reconstitute(
                new MemberId(id),
                new DiHash(entity.getDiHash()),
                entity.getStatus(),
                entity.getSanctionedUntil(),
                entity.getCreatedAt(),
                entity.getSocialIdentities().stream()
                        .map(i -> {
                            String email = decrypt(
                                    i.getEmailEnc(),
                                    emailContext(i.getProvider().name(), i.getProviderSubject()),
                                    "email_enc",
                                    id);
                            return new LinkedSocialIdentity(
                                    new SocialIdentity(i.getProvider(), i.getProviderSubject()),
                                    i.getLinkedAt(),
                                    email,
                                    email != null && i.isEmailVerified());
                        })
                        .toList(),
                decrypt(entity.getNameEnc(), "auth_account.name_enc:" + id, "name_enc", id),
                decrypt(entity.getPhoneEnc(), "auth_account.phone_enc:" + id, "phone_enc", id));
    }

    public AuthAccountJpaEntity toJpaEntity(AuthAccount account) {
        var id = account.memberId().value();
        return new AuthAccountJpaEntity(
                id,
                account.diHash().value(),
                account.status(),
                account.sanctionedUntil(),
                account.createdAt(),
                account.socialIdentities().stream()
                        .map(i -> new AuthSocialIdentityJpaEntity(
                                i.identity().provider(),
                                i.identity().subject(),
                                id,
                                i.linkedAt(),
                                encrypt(
                                        i.email(),
                                        emailContext(
                                                i.identity().provider().name(),
                                                i.identity().subject())),
                                i.emailVerified()))
                        .toList(),
                encrypt(account.name(), "auth_account.name_enc:" + id),
                encrypt(account.phoneNumber(), "auth_account.phone_enc:" + id));
    }

    private String encrypt(String value, String context) {
        return value == null ? null : cipher.encrypt(value, context);
    }

    private String decrypt(String value, String context, String field, UUID memberId) {
        if (value == null) return null;
        try {
            return cipher.decrypt(value, context);
        } catch (IllegalStateException ignored) {
            LoggerFactory.getLogger(AuthAccountMapper.class)
                    .warn("PII decryption failed field={} memberId={}", field, memberId);
            return null;
        }
    }

    static String emailContext(String provider, String subject) {
        return "auth_social_identity.email_enc:" + provider + ":" + subject;
    }
}
