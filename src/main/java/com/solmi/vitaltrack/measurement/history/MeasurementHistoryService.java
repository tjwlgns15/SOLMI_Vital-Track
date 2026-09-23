package com.solmi.vitaltrack.measurement.history;

import com.solmi.vitaltrack.measurement.MeasurementSession;
import com.solmi.vitaltrack.measurement.MeasurementSessionRepository;
import com.solmi.vitaltrack.measurement.SessionStatus;
import com.solmi.vitaltrack.measurement.realtime.AccelerationMessage;
import com.solmi.vitaltrack.measurement.realtime.EcgSampleMessage;
import com.solmi.vitaltrack.measurement.realtime.LocationMessage;
import com.solmi.vitaltrack.measurement.realtime.VelocityMessage;
import com.solmi.vitaltrack.member.Member;
import com.solmi.vitaltrack.member.MemberRepository;
import com.solmi.vitaltrack.subject.SubjectTypeLabels;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 측정 이력의 저장(실시간 수신 시점)과 조회(이력 목록/재생 데이터)를 함께 담당한다.
 * 다른 애그리거트 서비스들(SubjectService, MeasurementSessionService)과 동일하게
 * "측정 이력"이라는 하나의 책임 아래 쓰기/읽기를 함께 두는 스타일을 유지한다 (SRP는 애그리거트 단위).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeasurementHistoryService {

	private final MeasurementSessionRepository sessionRepository;
	private final MemberRepository memberRepository;
	private final LocationRecordRepository locationRecordRepository;
	private final EcgSampleRecordRepository ecgSampleRecordRepository;
	private final AccelerationRecordRepository accelerationRecordRepository;
	private final VelocityRecordRepository velocityRecordRepository;
	private final SubjectTypeLabels subjectTypeLabels;

	@Transactional
	public void recordLocation(MeasurementSession session, LocationMessage message) {
		locationRecordRepository.save(
				LocationRecord.of(session, message.latitude(), message.longitude(), toLocalDateTime(message.measuredAt())));
	}

	@Transactional
	public void recordEcg(MeasurementSession session, EcgSampleMessage message) {
		ecgSampleRecordRepository.save(
				EcgSampleRecord.of(session, message.samples(), message.samplingRateHz(), toLocalDateTime(message.measuredAt())));
	}

	@Transactional
	public void recordAcceleration(MeasurementSession session, AccelerationMessage message) {
		List<AccelerationRecord.AccelerationSample> samples = message.samples().stream()
				.map(s -> new AccelerationRecord.AccelerationSample(s.x(), s.y(), s.z()))
				.toList();
		accelerationRecordRepository.save(
				AccelerationRecord.of(session, samples, message.samplingRateHz(), toLocalDateTime(message.measuredAt())));
	}

	@Transactional
	public void recordVelocity(MeasurementSession session, VelocityMessage message) {
		velocityRecordRepository.save(
				VelocityRecord.of(session, message.speed(), toLocalDateTime(message.measuredAt())));
	}

	public List<SessionHistoryResponse> findEndedSessions(Long memberId) {
		Member owner = getMember(memberId);
		return sessionRepository.findByOwnerAndStatus(owner, SessionStatus.ENDED).stream()
				.map(session -> SessionHistoryResponse.from(session, subjectTypeLabels))
				.toList();
	}

	public PlaybackResponse getPlayback(Long sessionId, Long memberId) {
		MeasurementSession session = sessionRepository.findById(sessionId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 측정 세션입니다: " + sessionId));
		if (!session.getSubject().isOwnedBy(memberId)) {
			throw new IllegalStateException("본인이 등록한 측정 대상의 이력만 조회할 수 있습니다");
		}

		LocalDateTime baseline = session.getStartedAt();

		List<PlaybackResponse.LocationPoint> locations = locationRecordRepository.findBySessionOrderByMeasuredAtAsc(session)
				.stream()
				.map(record -> new PlaybackResponse.LocationPoint(
						record.getLatitude(), record.getLongitude(), offsetMs(baseline, record.getMeasuredAt())))
				.toList();

		List<PlaybackResponse.EcgBatch> ecgBatches = ecgSampleRecordRepository.findBySessionOrderByMeasuredAtAsc(session)
				.stream()
				.map(record -> new PlaybackResponse.EcgBatch(
						record.getSamples(), record.getSamplingRateHz(), offsetMs(baseline, record.getMeasuredAt())))
				.toList();

		List<PlaybackResponse.AccelerationBatch> accelerations = accelerationRecordRepository.findBySessionOrderByMeasuredAtAsc(session)
				.stream()
				.map(record -> new PlaybackResponse.AccelerationBatch(
						record.getSamples().stream()
								.map(s -> new PlaybackResponse.AccelerationBatch.AccelerationSample(s.x(), s.y(), s.z()))
								.toList(),
						record.getSamplingRateHz(),
						offsetMs(baseline, record.getMeasuredAt())))
				.toList();

		List<PlaybackResponse.VelocityPoint> velocities = velocityRecordRepository.findBySessionOrderByMeasuredAtAsc(session)
				.stream()
				.map(record -> new PlaybackResponse.VelocityPoint(
						record.getSpeed(), offsetMs(baseline, record.getMeasuredAt())))
				.toList();

		return new PlaybackResponse(
				SessionHistoryResponse.from(session, subjectTypeLabels), locations, ecgBatches, accelerations, velocities);
	}

	private Member getMember(Long memberId) {
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다: " + memberId));
	}

	private LocalDateTime toLocalDateTime(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
	}

	private long offsetMs(LocalDateTime baseline, LocalDateTime measuredAt) {
		return ChronoUnit.MILLIS.between(baseline, measuredAt);
	}
}
