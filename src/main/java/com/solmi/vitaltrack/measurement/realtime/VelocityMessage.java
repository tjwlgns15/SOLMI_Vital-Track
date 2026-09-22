package com.solmi.vitaltrack.measurement.realtime;

import java.time.Instant;

/**
 * 이동 속도(km/h) 스칼라 값.
 */
public record VelocityMessage(
		Long subjectId,
		double speed,
		Instant measuredAt
) {
}
