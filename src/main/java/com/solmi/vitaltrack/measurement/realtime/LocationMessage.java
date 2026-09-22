package com.solmi.vitaltrack.measurement.realtime;

import java.time.Instant;

/**
 * 시뮬레이터(폰 역할) → 서버로 전송되는 위치 데이터. 불변 record이므로 setter가 존재하지 않는다.
 */
public record LocationMessage(
		Long subjectId,
		double latitude,
		double longitude,
		Instant measuredAt
) {
}
