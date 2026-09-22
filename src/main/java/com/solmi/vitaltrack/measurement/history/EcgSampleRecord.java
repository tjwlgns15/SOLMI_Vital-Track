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
 * 심전도 파형 배치(batch) 1건의 기록. 샘플 배열은 별도 조인 테이블 없이
 * 콤마로 구분된 문자열로 저장하고, 외부에는 항상 List&lt;Double&gt;로만 노출한다
 * (저장 형식은 이 엔티티 내부 구현 세부사항으로 캡슐화 - 원본 문자열 getter는 제공하지 않는다).
 */
@Entity
@Table(name = "ecg_sample_record")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EcgSampleRecord extends BaseTimeEntity {

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

	// @Lob만으로는 MySQL에서 의도한 대로 큰 텍스트 컬럼이 생성되지 않을 수 있어
	// (짧은 길이로 생성되어 "Data too long" 오류가 났던 적이 있음) 타입을 명시적으로 고정한다.
	@Column(nullable = false, columnDefinition = "LONGTEXT")
	private String samplesCsv;

	// 재생 시 offsetMs(ms 단위) 계산에 쓰이므로 초 단위로 잘리지 않도록 밀리초 정밀도를 명시한다
	@Column(nullable = false, columnDefinition = "DATETIME(3)")
	@Getter
	private LocalDateTime measuredAt;

	private EcgSampleRecord(MeasurementSession session, List<Double> samples, int samplingRateHz, LocalDateTime measuredAt) {
		this.session = session;
		this.samplesCsv = encode(samples);
		this.samplingRateHz = samplingRateHz;
		this.measuredAt = measuredAt;
	}

	public static EcgSampleRecord of(MeasurementSession session, List<Double> samples, int samplingRateHz, LocalDateTime measuredAt) {
		return new EcgSampleRecord(session, samples, samplingRateHz, measuredAt);
	}

	public List<Double> getSamples() {
		return decode(samplesCsv);
	}

	private static String encode(List<Double> samples) {
		// 부동소수점 노이즈로 문자열이 불필요하게 길어지는 것을 막기 위해 소수점 4자리로 반올림한다.
		// 로케일에 따라 소수점이 ','로 바뀌는 것을 막기 위해 Locale.ROOT를 명시한다 (콤마는 구분자로 쓰이므로).
		return samples.stream()
				.map(d -> String.format(Locale.ROOT, "%.4f", d))
				.collect(Collectors.joining(","));
	}

	private static List<Double> decode(String csv) {
		if (csv == null || csv.isBlank()) {
			return List.of();
		}
		return java.util.Arrays.stream(csv.split(","))
				.map(Double::parseDouble)
				.toList();
	}
}
