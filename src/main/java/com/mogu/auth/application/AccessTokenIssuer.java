package com.mogu.auth.application;

import com.mogu.common.auth.MemberId;

public interface AccessTokenIssuer {
    IssuedAccessToken issue(MemberId memberId);
}
