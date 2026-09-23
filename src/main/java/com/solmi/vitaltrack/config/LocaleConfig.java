package com.solmi.vitaltrack.config;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/**
 * 다국어(i18n) 배선만 담당한다 (SRP). "어떤 언어로 좁힐지" 판단하는 실제 로직은
 * SupportedLocaleResolver에 있다 - WebSocketConfig가 STOMP 인증 로직을
 * StompAuthChannelInterceptor에 위임해두는 것과 같은 구조를 따른다.
 */
@Configuration
@RequiredArgsConstructor
public class LocaleConfig implements WebMvcConfigurer {

	private final MemberLanguagePreferenceInterceptor memberLanguagePreferenceInterceptor;

	/** 화면 어디서든 링크/드롭다운에 ?lang=ko 또는 ?lang=en 을 붙이면 그 요청부터 언어가 바뀌고,
	 *  쿠키(LocaleResolver 참고)로 계속 유지된다. */
	private static final String LANGUAGE_PARAM = "lang";

	@Bean
	public LocaleResolver localeResolver() {
		return new SupportedLocaleResolver();
	}

	@Bean
	public LocaleChangeInterceptor localeChangeInterceptor() {
		LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
		interceptor.setParamName(LANGUAGE_PARAM);
		return interceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(localeChangeInterceptor());
		// localeChangeInterceptor 다음 순서로 등록 - 언어가 실제로 바뀐 뒤에 그 선택을 기록한다.
		registry.addInterceptor(memberLanguagePreferenceInterceptor);
	}

	/**
	 * classpath의 messages_ko.properties / messages_en.properties (그리고 안전망으로
	 * messages.properties)를 읽는다. 키가 없을 때 예외를 던지는 대신 키 이름 자체를 그대로
	 * 보여주게 해서, 번역이 빠진 화면을 예외로 죽이지 않고 바로 눈에 띄게 한다.
	 */
	@Bean
	public MessageSource messageSource() {
		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
		messageSource.setBasename("messages");
		messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
		messageSource.setUseCodeAsDefaultMessage(true);
		return messageSource;
	}
}
