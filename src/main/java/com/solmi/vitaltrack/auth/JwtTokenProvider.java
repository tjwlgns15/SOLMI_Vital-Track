package com.solmi.vitaltrack.auth;

import com.solmi.vitaltrack.member.Member;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰(JWT)의 발급/검증/파싱만 담당한다.
 * 리프레시 토큰의 발급/검증/폐기는 별도 책임(RefreshTokenService)으로 분리했다 (SRP) -
 * 액세스 토큰은 서버에 저장하지 않는(stateless) 반면 리프레시 토큰은 DB에서 관리되어
 * 서로 성격이 다른 책임이기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

	private static final String CLAIM_MEMBER_ID = "memberId";
	private static final String CLAIM_ROLE = "role";

	private final JwtProperties jwtProperties;

	public String createAccessToken(Member member) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + jwtProperties.accessTokenValiditySeconds() * 1000);

		return Jwts.builder()
				.subject(member.getLoginId())
				.claim(CLAIM_MEMBER_ID, member.getId())
				.claim(CLAIM_ROLE, member.getRole().name())
				.issuedAt(now)
				.expiration(expiry)
				.signWith(signingKey())
				.compact();
	}

	public boolean validate(String token) {
		try {
			parseClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

	public Long getMemberId(String token) {
		return parseClaims(token).get(CLAIM_MEMBER_ID, Long.class);
	}

	private Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(signingKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	private SecretKey signingKey() {
		return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
	}
}
