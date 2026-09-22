package com.solmi.vitaltrack.measurement.realtime;

import com.solmi.vitaltrack.member.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/**
 * STOMP 프로토콜 어댑터. 수신한 메시지를 그대로 MeasurementIngestService에 위임한다
 * (검증/중계/저장 로직을 이 클래스에 두지 않음으로써 프로토콜 처리와 도메인 로직을 분리한다).
 *
 * <p>Authentication은 이 커넥션의 STOMP CONNECT 프레임을 인증한 StompAuthChannelInterceptor가
 * 세팅해둔 것을 그대로 주입받는다(연결이 유지되는 한 항상 존재함이 보장된다). "누가 보냈는지"를
 * 꺼내는 것까지가 프로토콜 어댑터의 역할이고, 그 사용자가 실제로 이 subjectId의 소유자인지
 * 검증하는 도메인 규칙은 MeasurementIngestService에 위임한다.</p>
 */
@Controller
@RequiredArgsConstructor
public class RealtimeRelayController {

	private final MeasurementIngestService ingestService;

	@MessageMapping("/subjects/{subjectId}/location")
	public void receiveLocation(@Payload LocationMessage message, Authentication authentication) {
		ingestService.ingestLocation(message, resolveMemberId(authentication));
	}

	@MessageMapping("/subjects/{subjectId}/ecg")
	public void receiveEcg(@Payload EcgSampleMessage message, Authentication authentication) {
		ingestService.ingestEcg(message, resolveMemberId(authentication));
	}

	@MessageMapping("/subjects/{subjectId}/acceleration")
	public void receiveAcceleration(@Payload AccelerationMessage message, Authentication authentication) {
		ingestService.ingestAcceleration(message, resolveMemberId(authentication));
	}

	@MessageMapping("/subjects/{subjectId}/velocity")
	public void receiveVelocity(@Payload VelocityMessage message, Authentication authentication) {
		ingestService.ingestVelocity(message, resolveMemberId(authentication));
	}

	private Long resolveMemberId(Authentication authentication) {
		return ((MemberPrincipal) authentication.getPrincipal()).getMemberId();
	}
}
