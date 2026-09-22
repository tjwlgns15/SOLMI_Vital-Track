package com.solmi.vitaltrack.measurement.activity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 세션별 최근 활동 상태 이력을 메모리에서만 관리한다 (SessionActivityTracker가 "마지막 데이터
 * 수신 시각"을 메모리로 추적하는 것과 같은 이유 - 매 배치마다 갱신되는 값을 DB row로 두면
 * 여러 WebSocket 스레드가 동시에 같은 row를 갱신하게 되어 락 경합을 유발한다).
 *
 * <p>"마지막으로 판정된 활동 수준"과는 다른 책임(그건 SessionActivityTracker가 아니라
 * 여기서 자체적으로 갖는다)이라 기존 SessionActivityTracker를 재사용하지 않고 별도로 둔다 (SRP).</p>
 */
@Component
public class ActivityStateTracker {

	private final Map<Long, ActivityHistory> historyBySessionId = new ConcurrentHashMap<>();

	public ActivityUpdateResult record(Long sessionId, ActivityLevel level, Instant now) {
		ActivityHistory history = historyBySessionId.computeIfAbsent(sessionId, id -> new ActivityHistory(level, now));
		return history.update(level, now);
	}

	public void forget(Long sessionId) {
		historyBySessionId.remove(sessionId);
	}
}

/**
 * 세션 1건의 활동 상태 이력. 상태가 바뀌었는지, 같은 상태(REST)가 얼마나 지속됐는지,
 * 최근 window 안에서 REST<->비REST 전환이 몇 번 있었는지를 스스로 판단한다 (도메인 로직을
 * ActivityStateTracker에 흩어놓지 않기 위해 별도 클래스로 둔다 - setter 없이 update() 하나로만
 * 상태를 바꾼다).
 */
class ActivityHistory {

	private static final Duration TRANSITION_WINDOW = Duration.ofMinutes(10);
	private static final Duration SUSTAINED_REST_THRESHOLD = Duration.ofMinutes(3);
	private static final int ABNORMAL_TRANSITION_THRESHOLD = 4;

	private final Deque<Instant> restTransitions = new ArrayDeque<>();
	private ActivityLevel currentLevel;
	private Instant currentLevelSince;
	private boolean sustainedRestAlerted;
	private boolean abnormalTransitionAlerted;

	ActivityHistory(ActivityLevel initialLevel, Instant now) {
		this.currentLevel = initialLevel;
		this.currentLevelSince = now;
	}

	ActivityUpdateResult update(ActivityLevel newLevel, Instant now) {
		boolean levelChanged = newLevel != currentLevel;
		if (levelChanged) {
			if (isRestTransition(currentLevel, newLevel)) {
				recordTransition(now);
			}
			currentLevel = newLevel;
			currentLevelSince = now;
			// 새 상태로 바뀌었으니, 그 상태에서의 "장시간 지속" 알림은 다시 자격을 얻는다.
			sustainedRestAlerted = false;
		}

		Duration currentLevelDuration = Duration.between(currentLevelSince, now);
		boolean sustainedRestNewlyDetected = false;
		if (currentLevel == ActivityLevel.REST && !sustainedRestAlerted
				&& currentLevelDuration.compareTo(SUSTAINED_REST_THRESHOLD) >= 0) {
			sustainedRestAlerted = true;
			sustainedRestNewlyDetected = true;
		}

		int transitionsInWindow = restTransitions.size();
		boolean abnormalNewlyDetected = false;
		if (transitionsInWindow >= ABNORMAL_TRANSITION_THRESHOLD) {
			if (!abnormalTransitionAlerted) {
				abnormalTransitionAlerted = true;
				abnormalNewlyDetected = true;
			}
		} else {
			// window에서 오래된 전환이 빠져나가 임계값 아래로 내려가면, 나중에 다시 임계값을
			// 넘었을 때 재알림할 수 있도록 자격을 되돌려둔다.
			abnormalTransitionAlerted = false;
		}

		return new ActivityUpdateResult(
				levelChanged, currentLevel, currentLevelDuration,
				sustainedRestNewlyDetected, transitionsInWindow, abnormalNewlyDetected);
	}

	private boolean isRestTransition(ActivityLevel from, ActivityLevel to) {
		return (from == ActivityLevel.REST) != (to == ActivityLevel.REST);
	}

	private void recordTransition(Instant now) {
		restTransitions.addLast(now);
		Instant cutoff = now.minus(TRANSITION_WINDOW);
		while (!restTransitions.isEmpty() && restTransitions.peekFirst().isBefore(cutoff)) {
			restTransitions.pollFirst();
		}
	}
}

/** {@link ActivityHistory#update}의 판정 결과. */
record ActivityUpdateResult(
		boolean levelChanged,
		ActivityLevel currentLevel,
		Duration currentLevelDuration,
		boolean sustainedRestNewlyDetected,
		int restTransitionsInWindow,
		boolean abnormalTransitionNewlyDetected) {
}
