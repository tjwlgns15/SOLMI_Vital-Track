package com.solmi.vitaltrack.measurement.activity;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * ActivityLevel을 화면 문구로 바꾸는 책임만 진다(SubjectTypeLabels와 같은 이유의 SRP).
 * 이 값은 실시간(WebSocket) 브로드캐스트에서만 쓰이고 HTTP 요청 스레드 밖에서 만들어지므로,
 * LocaleContextHolder에 기대지 않고 호출자가 Locale을 직접 넘기게 한다(호출자는
 * MemberLocaleResolver로 알림 받을 회원의 언어를 조회해서 넘긴다).
 */
@Component
public class ActivityLevelLabels {

	private final MessageSource messageSource;

	public ActivityLevelLabels(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	public String labelOf(ActivityLevel level, Locale locale) {
		return messageSource.getMessage(level.getMessageKey(), null, locale);
	}
}
