package com.solmi.vitaltrack.measurement;

import com.solmi.vitaltrack.measurement.activity.ActivityMonitorService;
import com.solmi.vitaltrack.member.Member;
import com.solmi.vitaltrack.member.MemberRepository;
import com.solmi.vitaltrack.subject.Subject;
import com.solmi.vitaltrack.subject.SubjectResponse;
import com.solmi.vitaltrack.subject.SubjectService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 측정 세션의 시작/종료/조회를 담당한다. 실시간 데이터 중계(WebSocket)는
 * realtime 패키지가 별도로 담당하므로 이 클래스는 세션 생명주기 관리에만 집중한다 (SRP).
 *
 * <p>세션이 시작/종료될 때 대시보드에 실시간으로 알려야 하지만, 그 알림(WebSocket 브로드캐스트)
 * 자체는 이 클래스의 책임이 아니다. realtime 패키지가 이 패키지에 의존하는 기존 방향을 거스르지
 * 않도록, 여기서는 도메인 이벤트만 발행하고 실제 알림은 별도 리스너(SessionLifecycleBroadcaster)가
 * 담당하게 한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeasurementSessionService {

	// 앱이 크래시 등으로 /end를 호출하지 못했다고 판단하는 기준 시간.
	// 실시간 데이터 전송 주기(가장 느린 velocity 기준 1초)에 비해 충분히 여유를 두어
	// 일시적인 네트워크 지연을 정상 세션의 타임아웃 오종료로 오인하지 않도록 한다.
	private static final Duration STALE_SESSION_THRESHOLD = Duration.ofSeconds(90);

	private final MeasurementSessionRepository sessionRepository;
	private final SubjectService subjectService;
	private final MemberRepository memberRepository;
	private final SessionActivityTracker activityTracker;
	private final ActivityMonitorService activityMonitorService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public SessionResponse start(Long subjectId, Long memberId) {
		Subject subject = subjectService.getOwnedSubject(subjectId, memberId);
		sessionRepository.findBySubjectAndStatus(subject, SessionStatus.ACTIVE)
				.ifPresent(existing -> {
					throw new IllegalStateException("이미 진행 중인 측정 세션이 있습니다");
				});
		MeasurementSession session = MeasurementSession.start(subject);
		// 위 조회와 저장 사이에는 시간차가 있어, 두 요청이 거의 동시에 들어오면
		// 조회 시점엔 둘 다 "활성 세션 없음"으로 통과할 수 있다(check-then-act 레이스).
		// id 전략이 IDENTITY라 save()가 INSERT를 즉시 실행하므로, 엔티티에 걸어둔
		// DB 유니크 제약 위반이 바로 이 지점에서 예외로 드러난다. 최종 방어선이므로
		// 사용자에게는 위와 동일한 메시지로 안내한다.
		try {
			sessionRepository.save(session);
		} catch (DataIntegrityViolationException e) {
			throw new IllegalStateException("이미 진행 중인 측정 세션이 있습니다");
		}
		eventPublisher.publishEvent(new MeasurementSessionStartedEvent(memberId, SubjectResponse.from(subject)));
		return SessionResponse.from(session);
	}

	@Transactional
	public SessionResponse end(Long sessionId, Long memberId) {
		MeasurementSession session = sessionRepository.findById(sessionId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 측정 세션입니다: " + sessionId));
		if (!session.getSubject().isOwnedBy(memberId)) {
			throw new IllegalStateException("본인이 등록한 측정 대상의 세션만 종료할 수 있습니다");
		}
		session.end();
		activityTracker.forget(sessionId);
		activityMonitorService.forgetSession(sessionId);
		eventPublisher.publishEvent(new MeasurementSessionEndedEvent(memberId, session.getSubjectId()));
		return SessionResponse.from(session);
	}

	public List<SessionResponse> findActiveSessions(Long memberId) {
		Member owner = getOwner(memberId);
		return sessionRepository.findByOwnerAndStatus(owner, SessionStatus.ACTIVE).stream()
				.map(SessionResponse::from)
				.toList();
	}

	/**
	 * 로그인한 계정의 측정 대상 중 지금 ACTIVE 세션이 있는(=측정 중인) 대상만 반환한다.
	 * 대시보드가 측정 중이 아닌 대상까지 노출하지 않도록 하기 위해 사용한다.
	 */
	public List<SubjectResponse> findSubjectsWithActiveSession(Long memberId) {
		Member owner = getOwner(memberId);
		return sessionRepository.findByOwnerAndStatus(owner, SessionStatus.ACTIVE).stream()
				.map(session -> SubjectResponse.from(session.getSubject()))
				.toList();
	}

	/**
	 * 실시간 데이터 수신(중계/이력 저장) 시 해당 대상의 활성 세션을 찾기 위해 사용한다.
	 * 활성 세션이 없으면 들어온 데이터를 무시해야 하므로 Optional로 반환한다.
	 */
	public Optional<MeasurementSession> findActiveSession(Subject subject) {
		return sessionRepository.findBySubjectAndStatus(subject, SessionStatus.ACTIVE);
	}

	/**
	 * 데이터 수신이 끊긴 채 {@link #STALE_SESSION_THRESHOLD} 이상 방치된 ACTIVE 세션을
	 * 타임아웃 종료 처리한다. 스케줄러(StaleMeasurementSessionScheduler)가 주기적으로 호출한다.
	 * "마지막 데이터 수신 시각"은 activityTracker(메모리)에서 조회해 판단에 사용한다.
	 *
	 * @return 이번 호출에서 타임아웃 종료된 세션 수
	 */
	@Transactional
	public int endStaleSessions() {
		LocalDateTime cutoff = LocalDateTime.now().minus(STALE_SESSION_THRESHOLD);
		List<MeasurementSession> staleSessions = sessionRepository.findByStatus(SessionStatus.ACTIVE).stream()
				.filter(session -> session.isStaleAsOf(cutoff, activityTracker.lastActivityOf(session.getId()).orElse(null)))
				.toList();
		staleSessions.forEach(session -> {
			session.endByTimeout();
			activityTracker.forget(session.getId());
			activityMonitorService.forgetSession(session.getId());
			eventPublisher.publishEvent(
					new MeasurementSessionEndedEvent(session.getSubject().getOwner().getId(), session.getSubjectId()));
		});
		return staleSessions.size();
	}

	private Member getOwner(Long memberId) {
		return memberRepository.findById(memberId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다: " + memberId));
	}
}
