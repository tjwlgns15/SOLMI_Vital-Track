package com.solmi.vitaltrack.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubjectRegisterRequest(

		@NotBlank(message = "이름을 입력해주세요")
		String name,

		@NotNull(message = "대상 종류를 선택해주세요")
		SubjectType type,

		String species
) {
}
