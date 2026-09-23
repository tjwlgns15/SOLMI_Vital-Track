package com.solmi.vitaltrack.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Locale;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

/**
 * 이 프로젝트는 한국어/영어만 지원한다(어떤 언어가 지원 언어인지는 SupportedLocales가 정한다).
 * 쿠키(LOCALE)에 저장된 언어가 있으면 그대로 쓰고, 없으면(첫 방문) 브라우저의 Accept-Language
 * 헤더를 기준으로 판단한다. 어느 경로로 들어오든(쿠키 / Accept-Language / 언어 전환 링크) 최종
 * 결과는 항상 지원 언어 중 하나로 좁혀지고, 지원하지 않는 언어면 한국어로 돌아간다 - 지원하지
 * 않는 언어 코드가 쿠키에 남아 메시지가 깨지는(키만 그대로 보이는) 상황을 막는다.
 */
public class SupportedLocaleResolver extends CookieLocaleResolver {

	public SupportedLocaleResolver() {
		setCookieName("LOCALE");
		setCookieMaxAge(Duration.ofDays(365));
	}

	/** 쿠키가 없는 첫 방문자에게만 적용되는 기본값 판단 로직 - 브라우저 Accept-Language를 지원 언어로 좁힌다. */
	@Override
	protected Locale determineDefaultLocale(HttpServletRequest request) {
		return SupportedLocales.closestTo(request.getLocale());
	}

	/** 언어 전환 링크(?lang=xx)로 들어오는 값도 반드시 지원 언어로 좁혀서 쿠키에 저장한다. */
	@Override
	public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
		super.setLocale(request, response, locale == null ? null : SupportedLocales.closestTo(locale));
	}
}
