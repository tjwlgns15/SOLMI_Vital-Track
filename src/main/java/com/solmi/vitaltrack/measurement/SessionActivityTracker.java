package com.solmi.vitaltrack.measurement;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 측정 세션의 "최근 데이터 수신 시각"을 메모리에서 추적한다.
 *
 * <p>이 값은 {@link StaleMeasurementSessionScheduler}가 30초 주기로 한 번씩만 참고하는 반면,
 * 실시간 데이터(ecg/velocity/acceleration/location)는 초당 수십~수백 건씩 여러 WebSocket 스레드에서
 * 동시에 들어올 수 있다. 이런 값을 매번 {@code MeasurementSession} 엔티티에 반영해 DB에 UPDATE하면
 * 같은 세션 row를 여러 트랜잭션이 동시에 갱신하게 되어 락 경합·데드락을 유발한다. 그래서
 * "최근 활동 감지"라는 책임을 엔티티(DB)에서 분리해 이 컴포넌트가 메모리에서만 전담한다 (SRP).</p>
 */
@Component
public class SessionActivityTracker {

	private final Map<Long, LocalDateTime> lastActivityBySessionId = new ConcurrentHashMap<>();

	/**
	 * 해당 세션에 데이터가 방금 도착했음을 기록한다.
	 * 실시간 데이터 수신 경로에서 호출되므로 DB 접근 없이 메모리에만 반영된다.
	 */
	public void recordActivity(Long sessionId) {
		lastActivityBySessionId.put(sessionId, LocalDateTime.now());
	}

	/**
	 * 해당 세션에 대해 기록된 마지막 활동 시각을 반환한다.
	 * 세션이 시작된 이후 데이터를 한 번도 받지 못했다면 비어있다.
	 */
	public Optional<LocalDateTime> lastActivityOf(Long sessionId) {
		return Optional.ofNullable(lastActivityBySessionId.get(sessionId));
	}

	/**
	 * 세션이 종료되어 더 이상 활동을 추적할 필요가 없을 때 호출해 메모리를 정리한다.
	 */
	public void forget(Long sessionId) {
		lastActivityBySessionId.remove(sessionId);
	}
}
