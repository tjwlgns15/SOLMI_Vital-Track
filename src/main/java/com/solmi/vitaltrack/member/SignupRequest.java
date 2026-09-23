package com.solmi.vitaltrack.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 입력값. record로 선언해 불변 + getter 자동 생성을 동시에 얻는다
 * (setter가 애초에 존재할 수 없는 구조).
 */
public record SignupRequest(

		@NotBlank(message = "{signup.error.loginId.blank}")
		@Size(min = 4, max = 30, message = "{signup.error.loginId.size}")
		String loginId,

		@NotBlank(message = "{signup.error.password.blank}")
		@Size(min = 4, max = 50, message = "{signup.error.password.size}")
		String password,

		@NotBlank(message = "{signup.error.name.blank}")
		String name
) {
}
