package com.solmi.vitaltrack.measurement.activity;

/**
 * 최근 일정 시간(window) 안에 정지<->활동 전환이 임계값 이상 반복되어 이상행동(산통 등)이
 * 의심될 때 발행되는 이벤트. 전환 횟수가 임계값 아래로 내려갔다가 다시 넘어야 재발행된다.
 */
public record AbnormalActivityDetectedEvent(Long memberId, Long subjectId, int transitionsInWindow) {
}
