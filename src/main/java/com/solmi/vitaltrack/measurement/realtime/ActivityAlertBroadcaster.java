package com.solmi.vitaltrack.measurement.realtime;

import com.solmi.vitaltrack.config.MemberLocaleResolver;
import com.solmi.vitaltrack.measurement.activity.AbnormalActivityDetectedEvent;
import com.solmi.vitaltrack.measurement.activity.ActivityLevelChangedEvent;
import com.solmi.vitaltrack.measurement.activity.ActivityLevelLabels;
import com.solmi.vitaltrack.measurement.activity.RestDetectedEvent;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * measurement.activity 패키지가 발행한 활동 분석 이벤트를 대시보드 구독자에게 실시간
 * 중계하는 것만 담당한다 (SessionLifecycleBroadcaster와 동일한 이유로 AFTER_COMMIT
 * 시점에 중계한다 - 이벤트를 발행한 ingestAcceleration()의 이력 저장 트랜잭션이 커밋된
 * 뒤에 알리기 위함).
 *
 * <p>이 브로드캐스트는 특정 HTTP 요청에 묶여 있지 않아 LocaleContextHolder로 언어를 판단할
 * 수 없다. 대신 이벤트가 이미 들고 있는 memberId로 그 회원이 마지막으로 고른 언어를
 * 조회해서(MemberLocaleResolver) 문구를 만든다 - AuthController가 DuplicateLoginIdException의
 * 문구를 구성하는 것과 같은 이유로, 문구 해석 책임은 이벤트/레코드가 아니라 이 클래스가 진다.</p>
 */
@Component
@RequiredArgsConstructor
public class ActivityAlertBroadcaster {

	private static final String TOPIC_PREFIX = "/topic/subjects/";

	private final SimpMessagingTemplate messagingTemplate;
	private final MessageSource messageSource;
	private final MemberLocaleResolver memberLocaleResolver;
	private final ActivityLevelLabels activityLevelLabels;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onActivityLevelChanged(ActivityLevelChangedEvent event) {
		Locale locale = memberLocaleResolver.resolve(event.memberId());
		String label = activityLevelLabels.labelOf(event.level(), locale);
		messagingTemplate.convertAndSend(
				TOPIC_PREFIX + event.subjectId() + "/activity", ActivityStatus.from(event.level(), label));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRestDetected(RestDetectedEvent event) {
		Locale locale = memberLocaleResolver.resolve(event.memberId());
		long minutes = event.restDuration().toMinutes();
		String message = messageSource.getMessage("activityAlert.rest", new Object[] {minutes}, locale);
		messagingTemplate.convertAndSend(TOPIC_PREFIX + event.subjectId() + "/alerts", ActivityAlert.rest(message));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAbnormalActivityDetected(AbnormalActivityDetectedEvent event) {
		Locale locale = memberLocaleResolver.resolve(event.memberId());
		String message = messageSource.getMessage(
				"activityAlert.abnormal", new Object[] {event.transitionsInWindow()}, locale);
		messagingTemplate.convertAndSend(TOPIC_PREFIX + event.subjectId() + "/alerts", ActivityAlert.abnormal(message));
	}
}
