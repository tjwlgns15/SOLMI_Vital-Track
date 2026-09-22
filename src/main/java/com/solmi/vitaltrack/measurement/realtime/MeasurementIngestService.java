package com.solmi.vitaltrack.measurement.realtime;

import com.solmi.vitaltrack.measurement.MeasurementSession;
import com.solmi.vitaltrack.measurement.MeasurementSessionService;
import com.solmi.vitaltrack.measurement.SessionActivityTracker;
import com.solmi.vitaltrack.measurement.history.MeasurementHistoryService;
import com.solmi.vitaltrack.subject.Subject;
import com.solmi.vitaltrack.subject.SubjectRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시뮬레이터(폰 역할)로부터 들어온 실시간 데이터를 받는 진입점.
 * "보낸 사람이 이 대상의 소유자인가"와 "이 대상에 지금 활성 세션이 있는가"를 이 클래스에서
 * 한 번 검증하고, 그 결과를 실시간 중계(RealtimeBroadcastService)와 이력 저장(MeasurementHistoryService)
 * 두 협력자에게 각각 위임한다 (각 협력자는 자신의 책임만 진다 - SRP).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeasurementIngestService {

	private final SubjectRepository subjectRepository;
	private final MeasurementSessionService measurementSessionService;
	private final RealtimeBroadcastService broadcastService;
	private final MeasurementHistoryService historyService;
	private final SessionActivityTracker activityTracker;

	// 모든 메서드가 이력 저장(쓰기)으로 이어질 수 있으므로 클래스 레벨 readOnly 트랜잭션을 두지 않고
	// 메서드마다 쓰기 가능한 트랜잭션을 명시한다 (readOnly 트랜잭션 안에서 INSERT가 실행되는 것을 방지).
	@Transactional
	public void ingestLocation(LocationMessage message, Long memberId) {
		resolveActiveSession(message.subjectId(), memberId).ifPresent(session -> {
			broadcastService.broadcastLocation(message.subjectId(), message);
			historyService.recordLocation(session, message);
		});
	}

	@Transactional
	public void ingestEcg(EcgSampleMessage message, Long memberId) {
		resolveActiveSession(message.subjectId(), memberId).ifPresent(session -> {
			broadcastService.broadcastEcg(message.subjectId(), message);
			historyService.recordEcg(session, message);
		});
	}

	@Transactional
	public void ingestAcceleration(AccelerationMessage message, Long memberId) {
		resolveActiveSession(message.subjectId(), memberId).ifPresent(session -> {
			broadcastService.broadcastAcceleration(message.subjectId(), message);
			historyService.recordAcceleration(session, message);
		});
	}

	@Transactional
	public void ingestVelocity(VelocityMessage message, Long memberId) {
		resolveActiveSession(message.subjectId(), memberId).ifPresent(session -> {
			broadcastService.broadcastVelocity(message.subjectId(), message);
			historyService.recordVelocity(session, message);
		});
	}

	private Optional<MeasurementSession> resolveActiveSession(Long subjectId, Long memberId) {
		Optional<Subject> subject = subjectRepository.findById(subjectId);
		if (subject.isEmpty()) {
			log.warn("존재하지 않는 측정 대상으로 실시간 데이터 수신: subjectId={}", subjectId);
			return Optional.empty();
		}
		if (!subject.get().isOwnedBy(memberId)) {
			// 인증된 사용자가 본인 소유가 아닌 subjectId로 데이터를 보낸 경우.
			// 정상적인 클라이언트라면 발생하지 않으므로, 조작된 요청 가능성이 있어 별도로 경고한다.
			log.warn("본인 소유가 아닌 측정 대상으로 실시간 데이터 수신 시도: subjectId={}, memberId={}", subjectId, memberId);
			return Optional.empty();
		}
		Optional<MeasurementSession> session = measurementSessionService.findActiveSession(subject.get());
		if (session.isEmpty()) {
			log.warn("활성 세션이 없는 대상으로 실시간 데이터 수신: subjectId={}", subjectId);
		} else {
			// 데이터가 계속 들어오고 있다는 증거이므로, 방치된 세션 판단 기준 시각을 메모리(activityTracker)에
			// 갱신한다. 매 메시지마다 DB row를 UPDATE하면 여러 WebSocket 스레드가 같은 row를 동시에
			// 갱신하게 되어 락 경합·데드락을 유발하므로, DB에는 쓰지 않는다.
			activityTracker.recordActivity(session.get().getId());
		}
		return session;
	}
}
