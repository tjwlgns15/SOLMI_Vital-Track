package com.solmi.vitaltrack.measurement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 앱 비정상 종료 등으로 /end 요청 없이 방치된 측정 세션을 주기적으로 정리한다.
 * "언제, 얼마나 자주 정리할지"만 이 클래스의 책임이고, "무엇을 방치된 세션으로 볼지·
 * 어떻게 종료할지" 판단은 MeasurementSessionService에 위임한다 (SRP).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleMeasurementSessionScheduler {

	private static final long FIXED_DELAY_MILLIS = 30_000;

	private final MeasurementSessionService sessionService;

	@Scheduled(fixedDelay = FIXED_DELAY_MILLIS)
	public void endStaleSessions() {
		int endedCount = sessionService.endStaleSessions();
		if (endedCount > 0) {
			log.info("장시간 데이터 수신이 없어 측정 세션 {}건을 타임아웃 종료했습니다", endedCount);
		}
	}
}
