package com.solmi.vitaltrack.config;

import com.solmi.vitaltrack.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 인증 방식 두 가지를 한 필터 체인 안에서 공존시킨다.
 * 1) 웹 브라우저 - 폼 로그인 + 세션 쿠키 + CSRF (기존과 동일, AuthController가 로그인 화면을 담당)
 * 2) 네이티브 앱(세션 쿠키를 쓸 수 없는 클라이언트) - /api/auth/** 에서 발급받은 JWT를
 *    Authorization: Bearer 헤더로 실어 보내는 방식 (JwtAuthenticationFilter가 검증)
 * 두 방식 모두 인증에 성공하면 이후 컨트롤러는 동일하게 MemberPrincipal을 사용하므로,
 * 기존 컨트롤러들(SubjectApiController 등)은 어느 쪽으로 인증했는지 신경 쓸 필요가 없다.
 * 실시간(WebSocket/STOMP) 채널은 이 HTTP 레벨 규칙과 별개로, STOMP CONNECT 프레임 단계에서
 * StompAuthChannelInterceptor가 인증을 담당한다 (WebSocketConfig, StompAuthChannelInterceptor 참고).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * JwtAuthenticationFilter는 아래 filterChain()에서 addFilterBefore로 Spring Security
	 * 체인에만 등록해서 쓴다. @Component가 붙은 Filter 빈은 Spring Boot가 기본적으로
	 * 서블릿 컨테이너 필터 체인에도 자동 등록해버려 요청마다 두 번 실행되므로, 그 자동 등록만
	 * 여기서 비활성화한다 (실제로는 멱등이라 동작엔 문제 없지만 불필요한 중복 실행을 막는다).
	 */
	@Bean
	public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
			JwtAuthenticationFilter jwtAuthenticationFilter) {
		FilterRegistrationBean<JwtAuthenticationFilter> registration =
				new FilterRegistrationBean<>(jwtAuthenticationFilter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/login", "/signup", "/css/**", "/js/**", "/api/auth/**").permitAll()
						// /ws 핸드셰이크 자체는 열어둔다. 세션 쿠키가 있는 요청은 (permitAll이어도)
						// 이 시점에 이미 SecurityContext가 채워져 있어 핸드셰이크의 Principal로 그대로 넘어가고,
						// JWT만 가진 네이티브 앱은 애초에 이 HTTP 요청에 토큰을 실을 방법이 마땅치 않으므로
						// (순수 WebSocket 핸드셰이크는 커스텀 헤더를 못 보내는 클라이언트가 많음) 여기서
						// 막지 않는다. 실제 인증 관문은 STOMP CONNECT 프레임을 검사하는
						// StompAuthChannelInterceptor가 맡는다 (WebSocketConfig 참고).
						.requestMatchers("/ws/**").permitAll()
						.anyRequest().authenticated()
				)
				// Bearer 토큰으로 인증되는 요청은 쿠키를 쓰지 않으므로 CSRF 공격 벡터 자체가 없다.
				// 로그인/재발급 요청도 아직 세션(따라서 CSRF 토큰)이 없는 시점이라 함께 제외한다.
				.csrf(csrf -> csrf.ignoringRequestMatchers(
						new AntPathRequestMatcher("/api/auth/**"),
						this::hasBearerToken
				))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(apiAwareEntryPoint()))
				.formLogin(form -> form
						.loginPage("/login")
						.defaultSuccessUrl("/dashboard", true)
						.permitAll()
				)
				.logout(logout -> logout
						.logoutSuccessUrl("/login")
						.permitAll()
				);

		return http.build();
	}

	/**
	 * /api/** 로 온 미인증 요청은 (앱 클라이언트가 JSON을 기대하므로) 로그인 화면으로 리다이렉트하지 않고
	 * 401로만 응답한다. 그 외 웹 화면 요청은 기존처럼 /login으로 리다이렉트한다.
	 */
	private AuthenticationEntryPoint apiAwareEntryPoint() {
		LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
		entryPoints.put(new AntPathRequestMatcher("/api/**"), new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));

		DelegatingAuthenticationEntryPoint delegating = new DelegatingAuthenticationEntryPoint(entryPoints);
		delegating.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
		return delegating;
	}

	/** Authorization: Bearer 헤더가 있는 요청은 쿠키가 아닌 토큰으로 인증되므로 CSRF 검증이 필요 없다. */
	private boolean hasBearerToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		return header != null && header.startsWith("Bearer ");
	}
}
