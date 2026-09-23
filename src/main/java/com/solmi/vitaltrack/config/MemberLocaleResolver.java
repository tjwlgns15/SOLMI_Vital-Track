package com.solmi.vitaltrack.config;

import com.solmi.vitaltrack.member.MemberRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 실시간(WebSocket) 알림처럼 HTTP 요청 스레드 밖에서 만들어지는 메시지는 LocaleContextHolder로
 * 언어를 판단할 수 없다(요청에 묶여 있지 않기 때문). 대신 알림을 받을 회원이 마지막으로
 * 언어 전환 드롭다운에서 고른 언어(Member.preferredLanguage, MemberLanguagePreferenceInterceptor가
 * 저장해둔 값)를 조회해서 쓴다. 한 번도 고른 적 없으면 기본값(한국어)이다.
 */
@Component
@RequiredArgsConstructor
public class MemberLocaleResolver {

	private final MemberRepository memberRepository;

	public Locale resolve(Long memberId) {
		return memberRepository.findById(memberId)
				.map(member -> SupportedLocales.fromLanguageTag(member.getPreferredLanguage()))
				.orElse(SupportedLocales.DEFAULT);
	}
}
