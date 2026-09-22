package com.solmi.vitaltrack.measurement.realtime;

import java.time.Instant;
import java.util.List;

/**
 * 심전도 파형 묶음. 매 샘플을 개별 전송하면 메시지 수가 지나치게 많아지므로
 * 일정 구간(batch)을 모아 전송한다.
 */
public record EcgSampleMessage(
		Long subjectId,
		List<Double> samples,
		int samplingRateHz,
		Instant measuredAt
) {
}
