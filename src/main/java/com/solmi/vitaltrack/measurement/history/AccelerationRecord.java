package com.solmi.vitaltrack.measurement.history;

import com.solmi.vitaltrack.common.BaseTimeEntity;
import com.solmi.vitaltrack.measurement.MeasurementSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가속도 파형 배치(batch) 1건의 기록. EcgSampleRecord와 동일한 방식으로,
 * 샘플 배열(x,y,z 묶음)은 별도 조인 테이블 없이 구분 문자열로 저장하고
 * 외부에는 항상 List&lt;AccelerationSample&gt;로만 노출한다 (저장 형식은 캡슐화).
 */
@Entity
@Table(name = "acceleration_record")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccelerationRecord extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Getter
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	@Getter
	private MeasurementSession session;

	@Column(nullable = false)
	@Getter
	private int samplingRateHz;

	// EcgSampleRecord와 동일한 이유로 컬럼 타입을 명시적으로 고정한다 (LONGTEXT).
	@Column(nullable = false, columnDefinition = "LONGTEXT")
	private String samplesCsv;

	// 재생 시 offsetMs(ms 단위) 계산에 쓰이므로 초 단위로 잘리지 않도록 밀리초 정밀도를 명시한다
	@Column(nullable = false, columnDefinition = "DATETIME(3)")
	@Getter
	private LocalDateTime measuredAt;

	private AccelerationRecord(MeasurementSession session, List<AccelerationSample> samples, int samplingRateHz, LocalDateTime measuredAt) {
		this.session = session;
		this.samplesCsv = encode(samples);
		this.samplingRateHz = samplingRateHz;
		this.measuredAt = measuredAt;
	}

	public static AccelerationRecord of(MeasurementSession session, List<AccelerationSample> samples, int samplingRateHz, LocalDateTime measuredAt) {
		return new AccelerationRecord(session, samples, samplingRateHz, measuredAt);
	}

	public List<AccelerationSample> getSamples() {
		return decode(samplesCsv);
	}

	private static String encode(List<AccelerationSample> samples) {
		// 부동소수점 노이즈로 문자열이 불필요하게 길어지는 것을 막기 위해 소수점 4자리로 반올림한다.
		// 로케일에 따라 소수점이 ','로 바뀌는 것을 막기 위해 Locale.ROOT를 명시한다 (콤마/콜론은 구분자로 쓰이므로).
		return samples.stream()
				.map(s -> String.format(Locale.ROOT, "%.4f:%.4f:%.4f", s.x(), s.y(), s.z()))
				.collect(Collectors.joining(","));
	}

	private static List<AccelerationSample> decode(String csv) {
		if (csv == null || csv.isBlank()) {
			return List.of();
		}
		return java.util.Arrays.stream(csv.split(","))
				.map(token -> {
					String[] parts = token.split(":");
					return new AccelerationSample(
							Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
				})
				.toList();
	}

	public record AccelerationSample(double x, double y, double z) {
	}
}
