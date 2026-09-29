package com.solmi.vitaltrack.auth;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 액세스/리프레시 토큰 관련 설정값. application.yml의 app.jwt.* 로 주입받는다.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		String secret,
		long accessTokenValiditySeconds,
		long refreshTokenValiditySeconds
) {

	// HMAC-SHA256 서명에 필요한 최소 키 길이. 이보다 짧으면 토큰을 만들 때 WeakKeyException이 난다.
	private static final int MIN_SECRET_BYTES = 32;

	/**
	 * 서명 키를 시작 시점에 검증한다. 운영에서 JWT_SECRET을 주입하지 않으면 Spring이
	 * "${JWT_SECRET}" 문자열을 그대로 넘겨서 서버는 정상으로 뜨고, 첫 로그인에서야 실패한다.
	 * 그래서 여기서 막아 서버가 아예 뜨지 않게 한다.
	 */
	public JwtProperties {
		if (secret == null || secret.isBlank() || secret.contains("${")) {
			throw new IllegalStateException(
					"app.jwt.secret이 설정되지 않았습니다. 운영에서는 JWT_SECRET 환경변수나 외부 설정 파일로 주입하세요");
		}
		if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"app.jwt.secret은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다 (생성 예: openssl rand -base64 48)");
		}
	}
}
