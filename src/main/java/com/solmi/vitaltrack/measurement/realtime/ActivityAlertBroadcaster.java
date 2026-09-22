package com.solmi.vitaltrack.measurement.realtime;

import com.solmi.vitaltrack.measurement.activity.AbnormalActivityDetectedEvent;
import com.solmi.vitaltrack.measurement.activity.ActivityLevelChangedEvent;
import com.solmi.vitaltrack.measurement.activity.RestDetectedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * measurement.activity 패키지가 발행한 활동 분석 이벤트를 대시보드 구독자에게 실시간
 * 중계하는 것만 담당한다 (SessionLifecycleBroadcaster와 동일한 이유로 AFTER_COMMIT
 * 시점에 중계한다 - 이벤트를 발행한 ingestAcceleration()의 이력 저장 트랜잭션이 커밋된
 * 뒤에 알리기 위함).
 */
@Component
@RequiredArgsConstructor
public class ActivityAlertBroadcaster {

	private static final String TOPIC_PREFIX = "/topic/subjects/";

	private final SimpMessagingTemplate messagingTemplate;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onActivityLevelChanged(ActivityLevelChangedEvent event) {
		messagingTemplate.convertAndSend(
				TOPIC_PREFIX + event.subjectId() + "/activity", ActivityStatus.from(event.level()));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRestDetected(RestDetectedEvent event) {
		messagingTemplate.convertAndSend(TOPIC_PREFIX + event.subjectId() + "/alerts", ActivityAlert.rest(event));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAbnormalActivityDetected(AbnormalActivityDetectedEvent event) {
		messagingTemplate.convertAndSend(TOPIC_PREFIX + event.subjectId() + "/alerts", ActivityAlert.abnormal(event));
	}
}
