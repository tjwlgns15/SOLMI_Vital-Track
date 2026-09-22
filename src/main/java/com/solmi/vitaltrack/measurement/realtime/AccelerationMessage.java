package com.solmi.vitaltrack.measurement.realtime;

import java.time.Instant;
import java.util.List;

/**
 * 3축 가속도 센서 값(m/s²) 묶음. ECG와 마찬가지로 매 샘플을 개별 전송하면 메시지 수가
 * 지나치게 많아지므로 일정 구간(batch)을 모아 전송한다. 정지 상태에서도 중력 성분이
 * 섞여 들어오는 실제 가속도계 특성을 그대로 반영한다.
 */
public record AccelerationMessage(
		Long subjectId,
		List<AccelerationSample> samples,
		int samplingRateHz,
		Instant measuredAt
) {

	public record AccelerationSample(double x, double y, double z) {
	}
}
