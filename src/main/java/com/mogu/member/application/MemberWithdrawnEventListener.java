package com.mogu.member.application;

import com.mogu.common.event.MemberWithdrawn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MemberWithdrawnEventListener {
    private final MemberProfileService service;

    public MemberWithdrawnEventListener(MemberProfileService service) {
        this.service = service;
    }

    @EventListener
    public void onMemberWithdrawn(MemberWithdrawn event) {
        service.withdraw(event.memberId(), event.withdrawnAt());
    }
}
