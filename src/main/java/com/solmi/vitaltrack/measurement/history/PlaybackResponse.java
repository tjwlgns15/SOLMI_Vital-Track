package com.solmi.vitaltrack.measurement.history;

import java.util.List;

/**
 * 재생 화면 하나가 필요로 하는 데이터를 한 번에 담아 전달한다.
 * 각 지점은 세션 시작 시각으로부터 몇 ms 지났는지(offsetMs)로 표현해
 * 클라이언트가 타임라인 재생 로직을 단순하게 구현할 수 있게 한다.
 */
public record PlaybackResponse(
		SessionHistoryResponse session,
		List<LocationPoint> locations,
		List<EcgBatch> ecgBatches,
		List<AccelerationBatch> accelerations,
		List<VelocityPoint> velocities
) {
	public record LocationPoint(double latitude, double longitude, long offsetMs) {
	}

	public record EcgBatch(List<Double> samples, int samplingRateHz, long offsetMs) {
	}

	public record AccelerationBatch(List<AccelerationSample> samples, int samplingRateHz, long offsetMs) {
		public record AccelerationSample(double x, double y, double z) {
		}
	}

	public record VelocityPoint(double speed, long offsetMs) {
	}
}
