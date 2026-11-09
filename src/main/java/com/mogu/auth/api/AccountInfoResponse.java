package com.mogu.auth.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mogu.auth.application.PiiMasking;
import com.mogu.auth.domain.AuthAccount;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.domain.LinkedSocialIdentity;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountInfoResponse(String name, String phoneNumber, List<LoginMethod> loginMethods) {
    public AccountInfoResponse {
        loginMethods = List.copyOf(loginMethods);
    }

    public static AccountInfoResponse from(AuthAccount account) {
        return new AccountInfoResponse(
                account.name(),
                PiiMasking.phoneNumber(account.phoneNumber()),
                account.socialIdentities().stream()
                        .sorted(Comparator.comparing(LinkedSocialIdentity::linkedAt))
                        .map(i -> new LoginMethod(
                                i.identity().provider(),
                                PiiMasking.email(i.email()),
                                PiiMasking.email(i.email()) != null && i.emailVerified(),
                                i.linkedAt()))
                        .toList());
    }

    @Override
    public String toString() {
        return "AccountInfoResponse[personalInfo=***]";
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginMethod(AuthProvider provider, String email, boolean emailVerified, Instant linkedAt) {
        @Override
        public String toString() {
            return "LoginMethod[personalInfo=***]";
        }
    }
}
