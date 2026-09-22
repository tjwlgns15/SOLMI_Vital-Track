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

@Getter
@Entity
@Table(name = "velocity_record")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VelocityRecord extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private MeasurementSession session;

	@Column(nullable = false)
	private double speed;

	// 재생 시 offsetMs(ms 단위) 계산에 쓰이므로 초 단위로 잘리지 않도록 밀리초 정밀도를 명시한다
	@Column(nullable = false, columnDefinition = "DATETIME(3)")
	private LocalDateTime measuredAt;

	private VelocityRecord(MeasurementSession session, double speed, LocalDateTime measuredAt) {
		this.session = session;
		this.speed = speed;
		this.measuredAt = measuredAt;
	}

	public static VelocityRecord of(MeasurementSession session, double speed, LocalDateTime measuredAt) {
		return new VelocityRecord(session, speed, measuredAt);
	}
}
