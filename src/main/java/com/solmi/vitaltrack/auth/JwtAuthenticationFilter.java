package com.solmi.vitaltrack.auth;

import com.solmi.vitaltrack.member.MemberPrincipal;
import com.solmi.vitaltrack.member.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer 헤더의 액세스 토큰을 검증해 SecurityContext를 채우는 필터.
 * 헤더가 없거나 토큰이 유효하지 않으면 그냥 다음 필터로 넘긴다 - 그 경우 기존 세션 기반
 * 인증(웹 브라우저)이 그대로 동작하고, 세션도 없다면 인증되지 않은 상태로 이어진다
 * (그 이후 처리는 SecurityConfig의 인가 규칙/AuthenticationEntryPoint가 담당한다 - SRP).
 * 이미 세션으로 인증된 요청까지 덮어쓰지 않도록, SecurityContext가 비어있을 때만 채운다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;
	private final MemberRepository memberRepository;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			resolveToken(request)
					.filter(jwtTokenProvider::validate)
					.map(jwtTokenProvider::getMemberId)
					.flatMap(memberRepository::findById)
					.ifPresent(member -> {
						MemberPrincipal principal = new MemberPrincipal(member);
						var authentication = new UsernamePasswordAuthenticationToken(
								principal, null, principal.getAuthorities());
						SecurityContextHolder.getContext().setAuthentication(authentication);
					});
		}

		filterChain.doFilter(request, response);
	}

	private Optional<String> resolveToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			return Optional.of(header.substring(BEARER_PREFIX.length()));
		}
		return Optional.empty();
	}
}
