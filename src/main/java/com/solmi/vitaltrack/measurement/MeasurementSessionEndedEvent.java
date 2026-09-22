package com.solmi.vitaltrack.measurement;

/**
 * 측정 세션이 종료되었음을 알리는 도메인 이벤트 (수동 종료와 타임아웃 종료 공통).
 * {@link MeasurementSessionStartedEvent}와 동일한 이유로 값만 담아 발행한다.
 */
public record MeasurementSessionEndedEvent(Long memberId, Long subjectId) {
}
