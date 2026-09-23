package com.solmi.vitaltrack.config;

import java.util.List;
import java.util.Locale;

/**
 * 이 프로젝트가 지원하는 언어(한국어/영어) 목록과, 임의의 Locale/언어 태그를 그 중
 * 하나로 좁히는 규칙만 담당한다. SupportedLocaleResolver(쿠키/Accept-Language 기반)와
 * MemberLocaleResolver(회원이 저장해둔 선호 언어 기반)가 이 규칙을 공유한다 - 둘 다
 * "무엇이 지원 언어인가"는 같은 기준을 따라야 하기 때문이다.
 */
public final class SupportedLocales {

	public static final List<Locale> ALL = List.of(Locale.KOREAN, Locale.ENGLISH);
	public static final Locale DEFAULT = Locale.KOREAN;

	private SupportedLocales() {
	}

	public static Locale closestTo(Locale candidate) {
		if (candidate == null) {
			return DEFAULT;
		}
		return ALL.stream()
				.filter(supported -> supported.getLanguage().equals(candidate.getLanguage()))
				.findFirst()
				.orElse(DEFAULT);
	}

	/** "ko", "en-US"처럼 문자열로 저장/전달된 언어 태그를 지원 언어로 좁힌다. null/빈 값이면 기본값. */
	public static Locale fromLanguageTag(String languageTag) {
		if (languageTag == null || languageTag.isBlank()) {
			return DEFAULT;
		}
		return closestTo(Locale.forLanguageTag(languageTag));
	}
}
