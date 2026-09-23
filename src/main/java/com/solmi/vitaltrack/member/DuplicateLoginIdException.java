package com.solmi.vitaltrack.member;

/**
 * 이미 사용 중인 아이디로 회원가입을 시도했을 때 발생한다. 화면에 보여줄 문구(언어)는 이
 * 예외가 아니라 이 예외를 잡는 표현 계층(AuthController)이 구성한다 - 도메인 서비스인
 * MemberService가 메시지 문구나 사용자의 언어(Locale)를 알 필요가 없도록 하기 위함이다.
 */
public class DuplicateLoginIdException extends RuntimeException {

	private final String loginId;

	public DuplicateLoginIdException(String loginId) {
		super("duplicate login id: " + loginId);
		this.loginId = loginId;
	}

	public String getLoginId() {
		return loginId;
	}
}
