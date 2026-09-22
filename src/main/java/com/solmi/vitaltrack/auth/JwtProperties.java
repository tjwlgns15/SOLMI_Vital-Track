package com.solmi.vitaltrack.auth;

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
}
