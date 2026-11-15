package com.mogu.auth.application;

import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalAccountQuery {
    private final AuthAccountRepository accounts;

    public WithdrawalAccountQuery(AuthAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public boolean isWithdrawn(MemberId memberId) {
        return accounts.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.UNAUTHORIZED))
                .isWithdrawn();
    }
}
