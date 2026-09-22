package com.solmi.vitaltrack.measurement.realtime;

import com.solmi.vitaltrack.measurement.MeasurementSessionEndedEvent;
import com.solmi.vitaltrack.measurement.MeasurementSessionStartedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 측정 세션의 시작/종료를 대시보드에 실시간으로 알리는 책임만 진다.
 *
 * <p>RealtimeBroadcastService가 "이미 검증된 데이터 메시지"의 중계만 담당하는 것과 같은 이유로,
 * "세션 생명주기가 바뀌었다는 사실의 중계"는 이 클래스가 별도로 전담한다 (SRP). 세션이 실제로
 * 커밋되기 전에 알림이 먼저 나가 클라이언트가 아직 존재하지 않는 세션을 조회하는 상황을 막기
 * 위해, 이벤트가 발행된 트랜잭션이 커밋된 이후에만 반응한다.</p>
 */
@Component
@RequiredArgsConstructor
public class SessionLifecycleBroadcaster {

	private static final String TOPIC_PREFIX = "/topic/members/";

	private final SimpMessagingTemplate messagingTemplate;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSessionStarted(MeasurementSessionStartedEvent event) {
		messagingTemplate.convertAndSend(TOPIC_PREFIX + event.memberId() + "/sessions/started", event.subject());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSessionEnded(MeasurementSessionEndedEvent event) {
		messagingTemplate.convertAndSend(TOPIC_PREFIX + event.memberId() + "/sessions/ended", event.subjectId());
	}
}
