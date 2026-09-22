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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 측정 세션 동안 수신된 위치 샘플 1건의 기록. 재생(playback) 기능이 조회 대상으로 사용한다.
 */
@Getter
@Entity
@Table(name = "location_record")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationRecord extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private MeasurementSession session;

	@Column(nullable = false)
	private double latitude;

	@Column(nullable = false)
	private double longitude;

	// 재생 시 offsetMs(ms 단위) 계산에 쓰이므로 초 단위로 잘리지 않도록 밀리초 정밀도를 명시한다
	@Column(nullable = false, columnDefinition = "DATETIME(3)")
	private LocalDateTime measuredAt;

	private LocationRecord(MeasurementSession session, double latitude, double longitude, LocalDateTime measuredAt) {
		this.session = session;
		this.latitude = latitude;
		this.longitude = longitude;
		this.measuredAt = measuredAt;
	}

	public static LocationRecord of(MeasurementSession session, double latitude, double longitude, LocalDateTime measuredAt) {
		return new LocationRecord(session, latitude, longitude, measuredAt);
	}
}
