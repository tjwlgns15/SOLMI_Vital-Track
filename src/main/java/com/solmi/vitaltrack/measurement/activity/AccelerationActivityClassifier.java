package com.solmi.vitaltrack.measurement.activity;

import com.solmi.vitaltrack.measurement.realtime.AccelerationMessage;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 가속도 샘플 배치 하나의 변동폭(표준편차)만으로 활동 수준을 판정하는 것만 담당한다 (SRP).
 * 세 축을 합친 가속도 크기(magnitude = sqrt(x^2+y^2+z^2))의 배치 내 표준편차를 본다 -
 * 축별 값이 아니라 magnitude를 쓰는 이유는, 중력 성분이 기기 방향에 따라 어느 축에 실릴지
 * 알 수 없어도(예: 목걸이형/다리형 등 부착 위치가 달라져도) "흔들림이 큰가"는 방향과 무관하게
 * 판단할 수 있기 때문이다.
 */
@Component
public class AccelerationActivityClassifier {

	// 시뮬레이터가 만들어내는 "보행" 패턴(x/y ±0.3, z 9.8±0.2 -> magnitude 표준편차 약 0.115)을
	// 기준으로 잡은 임계값이다. 실제 기기 데이터로 붙이게 되면 다시 보정이 필요할 수 있다.
	private static final double REST_THRESHOLD = 0.05;
	private static final double ACTIVE_THRESHOLD = 0.5;

	public ActivityLevel classify(List<AccelerationMessage.AccelerationSample> samples) {
		double stdDev = magnitudeStdDev(samples);
		if (stdDev < REST_THRESHOLD) {
			return ActivityLevel.REST;
		}
		if (stdDev < ACTIVE_THRESHOLD) {
			return ActivityLevel.WALKING;
		}
		return ActivityLevel.ACTIVE;
	}

	private double magnitudeStdDev(List<AccelerationMessage.AccelerationSample> samples) {
		double[] magnitudes = new double[samples.size()];
		for (int i = 0; i < samples.size(); i++) {
			AccelerationMessage.AccelerationSample s = samples.get(i);
			magnitudes[i] = Math.sqrt(s.x() * s.x() + s.y() * s.y() + s.z() * s.z());
		}
		double mean = 0;
		for (double m : magnitudes) {
			mean += m;
		}
		mean /= magnitudes.length;
		double variance = 0;
		for (double m : magnitudes) {
			variance += (m - mean) * (m - mean);
		}
		variance /= magnitudes.length;
		return Math.sqrt(variance);
	}
}
