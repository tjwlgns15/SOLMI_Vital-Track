package com.solmi.vitaltrack.measurement;

import com.solmi.vitaltrack.common.BaseTimeEntity;
import com.solmi.vitaltrack.subject.Subject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 측정 대상 1건에 대한 측정 세션의 생명주기(시작~종료)를 표현한다.
 * 상태 전이는 setter가 아니라 end()라는 도메인 의미가 분명한 메서드로만 가능하다.
 *
 * <p>같은 측정 대상에 대해 ACTIVE 세션이 둘 이상 존재해서는 안 된다. 서비스 계층의
 * "시작 전 조회 → 없으면 저장" 로직만으로는 두 요청이 거의 동시에 들어올 경우
 * (check-then-act) 레이스 컨디션을 막을 수 없으므로, activeSubjectId를 MySQL
 * 생성 컬럼(generated column)으로 두고 그 위에 유니크 제약을 걸어 DB 레벨에서
 * 최종적으로 중복을 차단한다. status가 ACTIVE일 때만 subject_id 값을 갖고
 * 그 외에는 NULL이 되며, MySQL의 유니크 인덱스는 NULL 값의 중복을 허용하므로
 * 종료된 세션끼리는 서로 충돌하지 않는다.</p>
 *
 * <p>"마지막으로 데이터를 받은 시각"은 이 엔티티가 필드로 들고 있지 않는다. 실시간 데이터가
 * 들어올 때마다 이 값을 DB에 반영하면 같은 row를 여러 WebSocket 스레드가 동시에 UPDATE하게 되어
 * 락 경합·데드락을 유발하기 때문이다. 대신 {@link SessionActivityTracker}가 메모리에서 전담해
 * 추적하고, 이 엔티티는 그 값을 외부에서 전달받아 도메인 규칙({@link #isStaleAsOf})만 판단한다.</p>
 */
@Getter
@Entity
@Table(name = "measurement_session",
		uniqueConstraints = @UniqueConstraint(name = "uk_measurement_session_active_subject", columnNames = "active_subject_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementSession extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subject_id", nullable = false)
	private Subject subject;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SessionStatus status;

	// DB 레벨 중복 방지용 생성 컬럼. 애플리케이션에서 직접 값을 넣거나 바꾸지 않으므로
	// insertable/updatable을 모두 false로 막아 Hibernate가 INSERT/UPDATE에 포함시키지 않게 한다.
	@Column(name = "active_subject_id", insertable = false, updatable = false,
			columnDefinition = "BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN subject_id ELSE NULL END) STORED")
	private Long activeSubjectId;

	// 이력 재생 시 각 기록과의 시간 차이(offsetMs)를 계산하는 기준이 되므로
	// 초 단위로 잘리지 않도록 밀리초 정밀도를 명시한다
	@Column(nullable = false, columnDefinition = "DATETIME(3)")
	private LocalDateTime startedAt;

	@Column(columnDefinition = "DATETIME(3)")
	private LocalDateTime endedAt;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private SessionEndReason endReason;

	private MeasurementSession(Subject subject) {
		this.subject = subject;
		this.status = SessionStatus.ACTIVE;
		this.startedAt = LocalDateTime.now();
	}

	public static MeasurementSession start(Subject subject) {
		return new MeasurementSession(subject);
	}

	public void end() {
		end(SessionEndReason.NORMAL);
	}

	/**
	 * 데이터 수신이 끊긴 채 일정 시간이 지난 세션을 서버가 대신 종료할 때 사용한다.
	 * (앱 크래시 등으로 사용자가 /end를 호출하지 못한 경우의 안전장치)
	 */
	public void endByTimeout() {
		end(SessionEndReason.TIMEOUT);
	}

	private void end(SessionEndReason reason) {
		if (this.status == SessionStatus.ENDED) {
			throw new IllegalStateException("이미 종료된 측정 세션입니다");
		}
		this.status = SessionStatus.ENDED;
		this.endedAt = LocalDateTime.now();
		this.endReason = reason;
	}

	/**
	 * cutoff 이후로 데이터 수신이 한 번도 없었다면 방치된(stale) 세션으로 판단한다.
	 * "마지막 데이터 수신 시각"은 이 엔티티가 아니라 {@link SessionActivityTracker}(메모리)가
	 * 들고 있으므로 호출부에서 조회해 전달한다. 아직 한 번도 데이터를 받지 못한 세션(null)은
	 * 시작 시각(startedAt)을 기준으로 삼는다.
	 */
	public boolean isStaleAsOf(LocalDateTime cutoff, LocalDateTime lastActivityAt) {
		LocalDateTime reference = (lastActivityAt != null) ? lastActivityAt : startedAt;
		return reference.isBefore(cutoff);
	}

	public boolean isActive() {
		return this.status == SessionStatus.ACTIVE;
	}

	public Long getSubjectId() {
		return subject.getId();
	}
}
