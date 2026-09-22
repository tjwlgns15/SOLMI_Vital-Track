package com.solmi.vitaltrack.auth;

import com.solmi.vitaltrack.member.Member;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 토큰의 발급/검증/폐기만 담당한다 (액세스 토큰 발급/검증은 JwtTokenProvider의 책임 - SRP).
 * 원문 토큰은 DB에 저장하지 않고 SHA-256 해시만 저장하며, 재발급(consume) 시 기존 토큰을
 * 즉시 폐기하는 1회용 로테이션 방식으로 탈취된 토큰의 재사용을 제한한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtProperties jwtProperties;

	@Transactional
	public String issue(Member member) {
		String rawToken = generateOpaqueToken();
		LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenValiditySeconds());
		refreshTokenRepository.save(RefreshToken.issue(member, hash(rawToken), expiresAt));
		return rawToken;
	}

	/** 리프레시 토큰을 검증하고 즉시 폐기(1회용)한 뒤 소유자를 반환한다. 호출자가 새 토큰을 다시 issue해야 한다. */
	@Transactional
	public Member consume(String rawToken) {
		RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawToken))
				.orElseThrow(() -> new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다"));
		if (!refreshToken.isUsable(LocalDateTime.now())) {
			throw new IllegalStateException("만료되었거나 이미 사용된 리프레시 토큰입니다. 다시 로그인해주세요");
		}
		refreshToken.revoke();
		return refreshToken.getMember();
	}

	@Transactional
	public void revoke(String rawToken) {
		refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
	}

	private String generateOpaqueToken() {
		byte[] bytes = new byte[64];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashed);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("해시 알고리즘을 사용할 수 없습니다", e);
		}
	}
}
