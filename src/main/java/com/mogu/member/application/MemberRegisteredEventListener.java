package com.mogu.member.application;

import com.mogu.common.event.MemberRegistered;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MemberRegisteredEventListener {
    private final MemberProfileService service;

    public MemberRegisteredEventListener(MemberProfileService service) {
        this.service = service;
    }

    @EventListener
    public void onMemberRegistered(MemberRegistered event) {
        service.createIncompleteProfile(new CreateMemberProfileCommand(event.memberId(), event.registeredAt()));
    }
}
