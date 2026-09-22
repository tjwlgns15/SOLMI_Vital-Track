package com.solmi.vitaltrack.auth;

import com.solmi.vitaltrack.member.Member;
import com.solmi.vitaltrack.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱(네이티브 클라이언트) 로그인/토큰 재발급/로그아웃을 조율한다.
 * 자격 증명 검증은 이 클래스가, 액세스 토큰 발급은 JwtTokenProvider가,
 * 리프레시 토큰 발급/검증/폐기는 RefreshTokenService가 각각 담당한다 (SRP).
 * 웹 브라우저의 폼 로그인(세션 기반)과는 별개의 인증 경로이며 서로 간섭하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenService refreshTokenService;

	@Transactional
	public TokenResponse login(LoginRequest request) {
		Member member = memberRepository.findByLoginId(request.loginId())
				.orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다"));
		if (!passwordEncoder.matches(request.password(), member.getPassword())) {
			throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다");
		}
		return issueTokens(member);
	}

	@Transactional
	public TokenResponse refresh(RefreshRequest request) {
		Member member = refreshTokenService.consume(request.refreshToken());
		return issueTokens(member);
	}

	@Transactional
	public void logout(RefreshRequest request) {
		refreshTokenService.revoke(request.refreshToken());
	}

	private TokenResponse issueTokens(Member member) {
		String accessToken = jwtTokenProvider.createAccessToken(member);
		String refreshToken = refreshTokenService.issue(member);
		return new TokenResponse(accessToken, refreshToken);
	}
}
