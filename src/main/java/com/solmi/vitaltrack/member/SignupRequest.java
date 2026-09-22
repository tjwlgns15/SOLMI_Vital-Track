package com.solmi.vitaltrack.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 입력값. record로 선언해 불변 + getter 자동 생성을 동시에 얻는다
 * (setter가 애초에 존재할 수 없는 구조).
 */
public record SignupRequest(

		@NotBlank(message = "아이디를 입력해주세요")
		@Size(min = 4, max = 30, message = "아이디는 4~30자여야 합니다")
		String loginId,

		@NotBlank(message = "비밀번호를 입력해주세요")
		@Size(min = 4, max = 50, message = "비밀번호는 4자 이상이어야 합니다")
		String password,

		@NotBlank(message = "이름을 입력해주세요")
		String name
) {
}
