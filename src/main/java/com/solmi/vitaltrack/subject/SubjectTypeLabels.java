package com.solmi.vitaltrack.subject;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * SubjectType을 현재 요청의 Locale에 맞는 화면 문구로 바꾸는 책임만 진다.
 * SubjectResponse/SessionResponse/SessionHistoryResponse처럼 대상 종류 문구가 필요한
 * 여러 DTO가 이 클래스 하나에 의존하게 해서, "어떤 문구를 보여줄지" 판단 로직이 여러 곳에
 * 흩어지지 않게 한다 (SRP).
 */
@Component
public class SubjectTypeLabels {

	private final MessageSource messageSource;

	public SubjectTypeLabels(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	/** 현재 요청(LocaleContextHolder)의 Locale 기준으로 문구를 반환한다. */
	public String labelOf(SubjectType type) {
		return labelOf(type, LocaleContextHolder.getLocale());
	}

	public String labelOf(SubjectType type, Locale locale) {
		return messageSource.getMessage(type.getMessageKey(), null, locale);
	}
}
