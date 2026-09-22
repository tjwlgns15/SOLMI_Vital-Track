package com.solmi.vitaltrack.measurement.activity;

import java.time.Duration;

/**
 * 정지(REST) 상태가 일정 시간 이상 지속되어(=누워서 휴식 중으로 추정) 처음 감지됐을 때
 * 발행되는 이벤트. 같은 REST 구간 동안 반복해서 발행되지 않는다(ActivityHistory가 edge-trigger로
 * 한 번만 올림).
 */
public record RestDetectedEvent(Long memberId, Long subjectId, Duration restDuration) {
}
