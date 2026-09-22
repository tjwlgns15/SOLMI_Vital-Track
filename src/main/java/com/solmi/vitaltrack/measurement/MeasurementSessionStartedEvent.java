package com.solmi.vitaltrack.measurement;

import com.solmi.vitaltrack.subject.SubjectResponse;

/**
 * 측정 세션이 시작되었음을 알리는 도메인 이벤트.
 *
 * <p>"세션을 시작한다"(MeasurementSessionService)와 "그 사실을 대시보드에 실시간으로 알린다"
 * (measurement.realtime 패키지)는 서로 다른 책임이므로, 세션 서비스는 이 이벤트를 발행하기만
 * 하고 실제 WebSocket 알림은 별도 리스너가 담당한다 (SRP). 리스너가 트랜잭션 커밋 이후
 * 시점에 안전하게 사용할 수 있도록 엔티티가 아니라 필요한 값만 담아 전달한다.</p>
 */
public record MeasurementSessionStartedEvent(Long memberId, SubjectResponse subject) {
}
