package com.solmi.vitaltrack.measurement.activity;

import com.solmi.vitaltrack.measurement.MeasurementSession;
import com.solmi.vitaltrack.measurement.realtime.AccelerationMessage;
import com.solmi.vitaltrack.subject.SubjectType;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 가속도 배치로 활동 수준을 판정하고(AccelerationActivityClassifier), 상태 변화/이상 패턴을
 * 감지해(ActivityStateTracker) 이벤트를 발행하는 것까지만 담당한다. 실제 알림 전달(WebSocket
 * 중계)은 measurement.realtime의 별도 리스너(ActivityAlertBroadcaster)가 담당한다 - 세션
 * 생명주기 이벤트와 동일한 방향(도메인 이벤트 발행 / 중계는 realtime 패키지)을 그대로 따른다.
 *
 * <p>사람(HUMAN) 대상에는 의미가 없는 분석이라 동물(ANIMAL) 대상에만 적용한다. 품종
 * 문자열로 "말"을 직접 매칭하지 않으므로, 나중에 다른 동물로 확장해도 이 조건은 그대로
 * 유지된다.</p>
 */
@Service
@RequiredArgsConstructor
public class ActivityMonitorService {

	private final AccelerationActivityClassifier classifier;
	private final ActivityStateTracker stateTracker;
	private final ApplicationEventPublisher eventPublisher;

	public void analyze(MeasurementSession session, AccelerationMessage message) {
		if (session.getSubject().getType() != SubjectType.ANIMAL) {
			return;
		}

		ActivityLevel level = classifier.classify(message.samples());
		Long subjectId = session.getSubjectId();
		Long memberId = session.getSubject().getOwner().getId();

		ActivityUpdateResult result = stateTracker.record(session.getId(), level, Instant.now());

		if (result.levelChanged()) {
			eventPublisher.publishEvent(new ActivityLevelChangedEvent(memberId, subjectId, result.currentLevel()));
		}
		if (result.sustainedRestNewlyDetected()) {
			eventPublisher.publishEvent(new RestDetectedEvent(memberId, subjectId, result.currentLevelDuration()));
		}
		if (result.abnormalTransitionNewlyDetected()) {
			eventPublisher.publishEvent(
					new AbnormalActivityDetectedEvent(memberId, subjectId, result.restTransitionsInWindow()));
		}
	}

	public void forgetSession(Long sessionId) {
		stateTracker.forget(sessionId);
	}
}
