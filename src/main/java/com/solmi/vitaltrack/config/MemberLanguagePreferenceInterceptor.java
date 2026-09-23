package com.solmi.vitaltrack.config;

import com.solmi.vitaltrack.member.MemberPrincipal;
import com.solmi.vitaltrack.member.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 언어 전환 드롭다운(?lang=xx)으로 언어를 바꾼 시점에, 로그인한 회원이면 그 선택을
 * 회원 정보에도 남긴다(MemberService.changePreferredLanguage) - "언어를 어떻게 판단할지"는
 * LocaleChangeInterceptor/SupportedLocaleResolver의 책임이고, 이 인터셉터는 "로그인한 회원의
 * 선택을 기억해두는 것"만 담당한다(SRP). 브라우저 쿠키(LOCALE)는 그 브라우저에서만 유지되지만,
 * 실시간(WebSocket) 알림처럼 쿠키에 접근할 수 없는 경로(MemberLocaleResolver)에서도 회원이
 * 마지막으로 고른 언어를 쓸 수 있게 하기 위함이다.
 */
@Component
@RequiredArgsConstructor
public class MemberLanguagePreferenceInterceptor implements HandlerInterceptor {

	private static final String LANGUAGE_PARAM = "lang";

	private final MemberService memberService;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String languageTag = request.getParameter(LANGUAGE_PARAM);
		if (languageTag == null || languageTag.isBlank()) {
			return true;
		}
		currentMemberId().ifPresent(memberId -> {
			Locale normalized = SupportedLocales.fromLanguageTag(languageTag);
			memberService.changePreferredLanguage(memberId, normalized.getLanguage());
		});
		return true;
	}

	private Optional<Long> currentMemberId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof MemberPrincipal principal)) {
			return Optional.empty();
		}
		return Optional.of(principal.getMemberId());
	}
}
