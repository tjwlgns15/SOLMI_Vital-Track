package com.solmi.vitaltrack.measurement.activity;

import com.solmi.vitaltrack.measurement.MeasurementSession;
import com.solmi.vitaltrack.measurement.MeasurementSessionRepository;
import com.solmi.vitaltrack.measurement.history.AccelerationRecord;
import com.solmi.vitaltrack.measurement.history.AccelerationRecordRepository;
import com.solmi.vitaltrack.measurement.realtime.AccelerationMessage;
import com.solmi.vitaltrack.subject.Subject;
import com.solmi.vitaltrack.subject.SubjectService;
import com.solmi.vitaltrack.subject.SubjectType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하루 단위 활동량 리포트를 "다시 계산"만으로 제공한다.
 *
 * <p>실시간 판정 상태({@link ActivityStateTracker})는 세션이 끝나면 메모리에서 사라지므로,
 * 리포트는 그 결과를 별도로 저장해두는 대신 원본 가속도 기록({@link AccelerationRecord})을
 * 실시간 경로와 똑같은 분류기({@link AccelerationActivityClassifier})와 똑같은 상태 전이
 * 규칙({@link ActivityHistory})에 다시 통과시켜 재현한다. 판정 로직을 두 곳에 따로 두지 않고
 * 하나로 유지함으로써, 나중에 임계값을 조정하면 과거 리포트도 항상 최신 기준으로 다시
 * 계산되고, 실시간 알림과 리포트 집계가 서로 다른 결과를 낼 수 없게 한다.</p>
 *
 * <p>단, 자정을 넘겨 이어지는 세션의 경우 리포트는 매일 자정에 상태를 새로 시작한 것으로
 * 계산한다(그 날짜의 원본 데이터만으로 재현하므로). 그래서 예를 들어 "3분 이상 지속된 휴식"이
 * 자정 직전에 시작해 자정 이후까지 이어지는 경우, 두 날짜 중 어느 쪽에도 그 알림이 집계되지
 * 않을 수 있다 - 실시간 알림은 세션 단위로 상태를 이어가지만, 리포트는 "그 날짜에 실제로
 * 벌어진 일"만 보기 때문이다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyActivityReportService {

	private final SubjectService subjectService;
	private final MeasurementSessionRepository sessionRepository;
	private final AccelerationRecordRepository accelerationRecordRepository;
	private final AccelerationActivityClassifier classifier;

	public DailyActivityReportResponse generate(Long subjectId, LocalDate date, Long memberId) {
		Subject subject = subjectService.getOwnedSubject(subjectId, memberId);
		if (subject.getType() != SubjectType.ANIMAL) {
			throw new IllegalStateException("활동량 리포트는 동물 대상에만 제공됩니다");
		}

		LocalDateTime dayStart = date.atStartOfDay();
		LocalDateTime dayEnd = dayStart.plusDays(1);

		List<MeasurementSession> sessions = sessionRepository.findBySubjectOverlapping(subject, dayStart, dayEnd);

		Accumulator accumulator = new Accumulator();
		for (MeasurementSession session : sessions) {
			replaySession(session, dayStart, dayEnd, accumulator);
		}

		return new DailyActivityReportResponse(
				subject.getId(), subject.getName(), date,
				accumulator.restSeconds, accumulator.walkingSeconds, accumulator.activeSeconds,
				accumulator.sustainedRestAlertCount, accumulator.abnormalAlertCount,
				sessions.size(), accumulator.hasData());
	}

	/** 세션 1건의, 그 날짜에 해당하는 구간만 다시 재생하며 누산기에 더한다. */
	private void replaySession(MeasurementSession session, LocalDateTime dayStart, LocalDateTime dayEnd, Accumulator accumulator) {
		List<AccelerationRecord> records = accelerationRecordRepository.findBySessionOrderByMeasuredAtAsc(session)
				.stream()
				.filter(record -> !record.getMeasuredAt().isBefore(dayStart) && record.getMeasuredAt().isBefore(dayEnd))
				.toList();

		// ActivityStateTracker.record()와 동일하게, 세션(여기서는 "세션의 그 날짜 구간")마다
		// 상태를 처음부터 새로 시작한다.
		ActivityHistory replay = null;
		for (AccelerationRecord record : records) {
			List<AccelerationMessage.AccelerationSample> samples = toMessageSamples(record);
			ActivityLevel level = classifier.classify(samples);
			accumulator.addDuration(level, batchSeconds(record, samples.size()));

			Instant measuredAt = toInstant(record.getMeasuredAt());
			if (replay == null) {
				replay = new ActivityHistory(level, measuredAt);
			}
			accumulator.addSignals(replay.update(level, measuredAt));
		}
	}

	private List<AccelerationMessage.AccelerationSample> toMessageSamples(AccelerationRecord record) {
		return record.getSamples().stream()
				.map(s -> new AccelerationMessage.AccelerationSample(s.x(), s.y(), s.z()))
				.toList();
	}

	/** 배치 하나가 실제로 몇 초 분량의 데이터인지를, 저장된 샘플링 레이트 기준으로 역산한다. */
	private long batchSeconds(AccelerationRecord record, int sampleCount) {
		int rateHz = record.getSamplingRateHz();
		return rateHz > 0 ? Math.round(sampleCount / (double) rateHz) : 0;
	}

	private Instant toInstant(LocalDateTime dateTime) {
		return dateTime.atZone(ZoneId.systemDefault()).toInstant();
	}

	/**
	 * 리포트 계산 도중의 누적값만 담당하는 작은 헬퍼. 외부에는 세터 대신 의미가 분명한
	 * addXxx() 메서드로만 상태 변경을 허용한다.
	 */
	private static class Accumulator {
		private long restSeconds;
		private long walkingSeconds;
		private long activeSeconds;
		private int sustainedRestAlertCount;
		private int abnormalAlertCount;

		void addDuration(ActivityLevel level, long seconds) {
			switch (level) {
				case REST -> restSeconds += seconds;
				case WALKING -> walkingSeconds += seconds;
				case ACTIVE -> activeSeconds += seconds;
			}
		}

		void addSignals(ActivityUpdateResult result) {
			if (result.sustainedRestNewlyDetected()) {
				sustainedRestAlertCount++;
			}
			if (result.abnormalTransitionNewlyDetected()) {
				abnormalAlertCount++;
			}
		}

		boolean hasData() {
			return restSeconds + walkingSeconds + activeSeconds > 0;
		}
	}
}
