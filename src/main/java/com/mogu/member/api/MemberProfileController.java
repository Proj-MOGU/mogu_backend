package com.mogu.member.api;

import com.mogu.common.auth.LoginMember;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import com.mogu.common.error.ErrorResponse;
import com.mogu.member.application.MemberProfileService;
import com.mogu.member.application.UpdateMyProfileCommand;
import com.mogu.member.domain.MemberErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@Tag(name = "회원", description = "회원 프로필 조회·입력·수정")
public class MemberProfileController {
    private final MemberProfileService service;

    public MemberProfileController(MemberProfileService service) {
        this.service = service;
    }

    @GetMapping("/me")
    @Operation(
            summary = "내 프로필 조회",
            description = "가입 직후 닉네임 입력 전이면 completed=false이고 nickname은 생략됩니다. "
                    + "nicknameChangeableAt은 지금 닉네임을 바꿀 수 없을 때만 포함됩니다.")
    @ApiResponse(responseCode = "200", description = "내 프로필 조회 성공")
    @ApiResponse(
            responseCode = "401",
            description = "로그인 필요 (AUTH_UNAUTHORIZED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "회원 프로필 없음 (MEMBER_PROFILE_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public MyProfileResponse getMyProfile(@LoginMember MemberId memberId) {
        return MyProfileResponse.from(service.getMyProfile(memberId));
    }

    @PutMapping("/me")
    @Operation(
            summary = "내 프로필 입력·수정",
            description =
                    "닉네임은 필수이며 30일에 한 번만 바꿀 수 있습니다. 처음 입력은 변경 횟수에 포함하지 않습니다. " + "PUT 요청이므로 bio를 생략하면 기존 한줄소개가 지워집니다.")
    @ApiResponse(responseCode = "200", description = "내 프로필 입력·수정 성공")
    @ApiResponse(
            responseCode = "400",
            description =
                    "입력값 오류 (COMMON_INVALID_INPUT, MEMBER_INVALID_NICKNAME, MEMBER_RESERVED_NICKNAME, MEMBER_INVALID_BIO)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "로그인 필요 (AUTH_UNAUTHORIZED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "회원 프로필 없음 (MEMBER_PROFILE_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "닉네임 중복 또는 변경 주기 위반 (MEMBER_DUPLICATE_NICKNAME, MEMBER_NICKNAME_CHANGE_TOO_SOON)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public MyProfileResponse updateMyProfile(
            @LoginMember MemberId memberId, @Valid @RequestBody UpdateMyProfileRequest request) {
        return MyProfileResponse.from(
                service.updateMyProfile(new UpdateMyProfileCommand(memberId, request.nickname(), request.bio())));
    }

    @GetMapping("/{memberId}")
    @Operation(
            summary = "다른 회원 프로필 조회",
            description =
                    "회원 ID와 공개 항목인 닉네임, 한줄소개만 반환합니다. " + "없는 회원, 닉네임 입력 전 회원, UUID 형식이 아닌 값은 모두 같은 404 오류를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "다른 회원 프로필 조회 성공")
    @ApiResponse(
            responseCode = "401",
            description = "로그인 필요 (AUTH_UNAUTHORIZED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "회원 프로필 없음 (MEMBER_PROFILE_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PublicProfileResponse getPublicProfile(
            @Parameter(description = "조회할 회원 ID (UUID)", example = "0193a5b0-7c1e-7d2a-9f00-1a2b3c4d5e6f") @PathVariable
                    String memberId) {
        MemberId id;
        try {
            id = MemberId.from(memberId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(MemberErrorCode.PROFILE_NOT_FOUND);
        }
        return PublicProfileResponse.from(service.getPublicProfile(id));
    }
}
