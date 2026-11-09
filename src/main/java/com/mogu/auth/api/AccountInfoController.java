package com.mogu.auth.api;

import com.mogu.auth.application.AccountInfoService;
import com.mogu.common.auth.LoginMember;
import com.mogu.common.auth.MemberId;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountInfoController {
    private final AccountInfoService service;

    public AccountInfoController(AccountInfoService service) {
        this.service = service;
    }

    @GetMapping("/api/auth/me/account")
    public ResponseEntity<AccountInfoResponse> account(@LoginMember MemberId memberId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AccountInfoResponse.from(service.getAccount(memberId)));
    }
}
