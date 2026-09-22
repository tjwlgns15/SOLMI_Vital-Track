package com.solmi.vitaltrack.auth;

/**
 * 로그인/재발급 성공 시 앱에 내려주는 토큰 쌍.
 * accessToken은 이후 모든 API 호출에 Authorization: Bearer &lt;accessToken&gt; 헤더로 실어 보내고,
 * refreshToken은 accessToken 만료 시 /api/auth/refresh 요청에만 사용한다 (안전한 저장소에 보관).
 */
public record TokenResponse(
		String accessToken,
		String refreshToken
) {
}
