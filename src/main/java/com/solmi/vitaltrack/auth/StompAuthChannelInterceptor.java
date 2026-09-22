package com.solmi.vitaltrack.auth;

import com.solmi.vitaltrack.member.MemberPrincipal;
import com.solmi.vitaltrack.member.MemberRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT 프레임 단계에서 인증을 처리한다.
 *
 * <p>HTTP 요청과 달리 WebSocket 연결은 (특히 순수 WebSocket 클라이언트의 경우) 핸드셰이크 요청에
 * 커스텀 헤더를 실어 보내기 어렵기 때문에, JWT는 HTTP 헤더가 아니라 STOMP CONNECT 프레임 자체의
 * 헤더로 전달받아 여기서 검증한다. 웹 브라우저는 /ws 핸드셰이크 시점에 이미 세션 쿠키로 인증되어
 * {@link StompHeaderAccessor#getUser()}가 채워져 있으므로 그 경우엔 아무 것도 하지 않고 통과시킨다.
 * 두 경우 모두 인증에 실패하면 연결 자체를 거부한다 (SecurityConfig가 /ws/** 를 permitAll로 열어두는
 * 대신, 실제 인증 관문 역할을 이 인터셉터가 맡는다).
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;
	private final MemberRepository memberRepository;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
			authenticate(accessor);
		}
		return message;
	}

	private void authenticate(StompHeaderAccessor accessor) {
		if (accessor.getUser() != null) {
			// 세션 쿠키로 핸드셰이크 시점에 이미 인증된 경우(웹 브라우저) - 그대로 둔다.
			return;
		}

		MemberPrincipal principal = resolveToken(accessor)
				.filter(jwtTokenProvider::validate)
				.map(jwtTokenProvider::getMemberId)
				.flatMap(memberRepository::findById)
				.map(MemberPrincipal::new)
				.orElseThrow(() -> new MessagingException("인증되지 않은 WebSocket 연결입니다"));

		accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
	}

	private Optional<String> resolveToken(StompHeaderAccessor accessor) {
		List<String> headers = accessor.getNativeHeader(AUTHORIZATION_HEADER);
		if (headers == null || headers.isEmpty()) {
			return Optional.empty();
		}
		String header = headers.get(0);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			return Optional.of(header.substring(BEARER_PREFIX.length()));
		}
		return Optional.empty();
	}
}
