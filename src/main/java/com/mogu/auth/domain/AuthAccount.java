package com.mogu.auth.domain;

import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AuthAccount {
    private final String name;
    private final String phoneNumber;
    private final MemberId memberId;
    private final DiHash diHash;
    private final AccountStatus status;
    private final Instant sanctionedUntil;
    private final Instant createdAt;
    private final List<LinkedSocialIdentity> socialIdentities;

    private AuthAccount(
            MemberId memberId,
            DiHash diHash,
            AccountStatus status,
            Instant sanctionedUntil,
            Instant createdAt,
            List<LinkedSocialIdentity> socialIdentities,
            String name,
            String phoneNumber) {
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        if (status != AccountStatus.SANCTIONED && sanctionedUntil != null)
            throw new IllegalArgumentException("Invalid sanction state");
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.memberId = memberId;
        this.diHash = Objects.requireNonNull(diHash);
        this.status = status;
        this.sanctionedUntil = sanctionedUntil;
        this.createdAt = createdAt;
        this.socialIdentities = List.copyOf(socialIdentities);
    }

    public static AuthAccount reconstitute(
            MemberId memberId,
            DiHash diHash,
            AccountStatus status,
            Instant sanctionedUntil,
            Instant createdAt,
            List<LinkedSocialIdentity> socialIdentities,
            String name,
            String phoneNumber) {
        return new AuthAccount(
                memberId, diHash, status, sanctionedUntil, createdAt, socialIdentities, name, phoneNumber);
    }

    public static AuthAccount register(MemberId memberId, DiHash diHash, SocialIdentity first, Instant now) {
        return new AuthAccount(
                memberId,
                diHash,
                AccountStatus.ACTIVE,
                null,
                now,
                List.of(new LinkedSocialIdentity(first, now)),
                null,
                null);
    }

    public static AuthAccount reconstitute(
            MemberId memberId,
            DiHash diHash,
            AccountStatus status,
            Instant sanctionedUntil,
            Instant createdAt,
            List<LinkedSocialIdentity> socialIdentities) {
        return reconstitute(memberId, diHash, status, sanctionedUntil, createdAt, socialIdentities, null, null);
    }

    public String name() {
        return name;
    }

    public String phoneNumber() {
        return phoneNumber;
    }

    public AuthAccount updatePersonalInfo(String name, String phoneNumber) {
        return new AuthAccount(
                memberId, diHash, status, sanctionedUntil, createdAt, socialIdentities, name, phoneNumber);
    }

    public AuthAccount updateSocialEmail(SocialIdentity social, String email, boolean verified) {
        if (email == null) return this;
        var updated = socialIdentities.stream()
                .map(i -> i.identity().equals(social)
                        ? new LinkedSocialIdentity(i.identity(), i.linkedAt(), email, verified)
                        : i)
                .toList();
        return new AuthAccount(memberId, diHash, status, sanctionedUntil, createdAt, updated, name, phoneNumber);
    }

    public DiHash diHash() {
        return diHash;
    }

    public AuthAccount link(SocialIdentity identity, Instant now) {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(now);
        if (socialIdentities.stream().anyMatch(i -> i.identity().equals(identity))) return this;
        if (socialIdentities.stream().anyMatch(i -> i.identity().provider() == identity.provider()))
            throw new BusinessException(AuthErrorCode.PROVIDER_ALREADY_LINKED);
        var linked = new ArrayList<>(socialIdentities);
        linked.add(new LinkedSocialIdentity(identity, now));
        return new AuthAccount(memberId, diHash, status, sanctionedUntil, createdAt, linked, name, phoneNumber);
    }

    public AuthAccount sanction(Instant until) {
        return new AuthAccount(
                memberId, diHash, AccountStatus.SANCTIONED, until, createdAt, socialIdentities, name, phoneNumber);
    }

    public AuthAccount lift() {
        return new AuthAccount(
                memberId, diHash, AccountStatus.ACTIVE, null, createdAt, socialIdentities, name, phoneNumber);
    }

    public MemberId memberId() {
        return memberId;
    }

    public AccountStatus status() {
        return status;
    }

    public Instant sanctionedUntil() {
        return sanctionedUntil;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<LinkedSocialIdentity> socialIdentities() {
        return socialIdentities;
    }

    public boolean isSanctionedAt(Instant now) {
        Objects.requireNonNull(now);
        return status == AccountStatus.SANCTIONED && (sanctionedUntil == null || sanctionedUntil.isAfter(now));
    }
}
