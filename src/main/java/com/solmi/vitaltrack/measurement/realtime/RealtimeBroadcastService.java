package com.solmi.vitaltrack.measurement.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 이미 유효성 검증이 끝난 메시지를 대시보드 구독자에게 중계(broadcast)하는 책임만 진다.
 * 유효성 검증(대상 존재/활성 세션 여부)은 MeasurementIngestService가 담당한다 (SRP).
 */
@Service
@RequiredArgsConstructor
public class RealtimeBroadcastService {

	private static final String TOPIC_PREFIX = "/topic/subjects/";

	private final SimpMessagingTemplate messagingTemplate;

	public void broadcastLocation(Long subjectId, LocationMessage message) {
		messagingTemplate.convertAndSend(TOPIC_PREFIX + subjectId + "/location", message);
	}

	public void broadcastEcg(Long subjectId, EcgSampleMessage message) {
		messagingTemplate.convertAndSend(TOPIC_PREFIX + subjectId + "/ecg", message);
	}

	public void broadcastAcceleration(Long subjectId, AccelerationMessage message) {
		messagingTemplate.convertAndSend(TOPIC_PREFIX + subjectId + "/acceleration", message);
	}

	public void broadcastVelocity(Long subjectId, VelocityMessage message) {
		messagingTemplate.convertAndSend(TOPIC_PREFIX + subjectId + "/velocity", message);
	}
}
