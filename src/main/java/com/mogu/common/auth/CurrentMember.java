package com.mogu.common.auth;

import java.util.Optional;

/**
 * 현재 요청 스레드에서 인증된 회원의 식별자를 조회하는 공통 계약이다.
 * 컨트롤러, 서비스, MVC 인터셉터에서 보안 필터 실행 이후 같은 요청 스레드 안에서 사용할 수 있다.
 * 로그인 없는 경로에서 인증이 없거나, 가입 대기 토큰 또는 익명 인증인 경우에는 빈 값을 반환한다.
 * 인증 컨텍스트가 전달되지 않은 다른 스레드에서도 빈 값을 반환한다.
 * 비동기 작업 등 다른 스레드에는 이 계약으로 다시 조회하지 말고, 조회한 회원 식별자를 인자로 넘긴다.
 *
 * <pre>{@code
 * public class MyService {
 *     private final CurrentMember currentMember;
 *
 *     public MyService(CurrentMember currentMember) {
 *         this.currentMember = currentMember;
 *     }
 *
 *     public void execute() {
 *         currentMember.find().ifPresent(memberId -> loadFor(memberId));
 *     }
 * }
 * }</pre>
 */
public interface CurrentMember {
    /** @return 인증된 회원의 식별자. 현재 스레드에 회원 인증이 없으면 빈 값 */
    Optional<MemberId> find();
}
