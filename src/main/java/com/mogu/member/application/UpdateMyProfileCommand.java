package com.mogu.member.application;

import com.mogu.common.auth.MemberId;

public record UpdateMyProfileCommand(MemberId memberId, String nickname, String bio) {}
