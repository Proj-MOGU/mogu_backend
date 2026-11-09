package com.mogu.auth.application;

import com.mogu.auth.domain.AuthAccount;
import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountInfoService {
    private final AuthAccountRepository accounts;

    public AccountInfoService(AuthAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public AuthAccount getAccount(MemberId memberId) {
        return accounts.findByMemberId(memberId).orElseThrow(() -> new BusinessException(AuthErrorCode.UNAUTHORIZED));
    }
}
