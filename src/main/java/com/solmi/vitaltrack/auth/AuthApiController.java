package com.solmi.vitaltrack.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 네이티브 앱(및 세션 쿠키를 쓸 수 없는 클라이언트)을 위한 JWT 발급 API.
 * 웹 브라우저 로그인(폼 로그인 + 세션, AuthController/SecurityConfig)과는 별개의 인증 수단이다.
 * 이 API로 로그인한 클라이언트는 이후 모든 API 호출에 Authorization: Bearer &lt;accessToken&gt;
 * 헤더를 실어 보내면 되고, 그 검증은 JwtAuthenticationFilter가 담당한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

	private final AuthService authService;

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return authService.refresh(request);
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
		authService.logout(request);
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
	public ResponseEntity<String> handleBadRequest(RuntimeException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
	}
}
