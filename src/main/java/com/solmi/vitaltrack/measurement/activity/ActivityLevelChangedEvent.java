package com.solmi.vitaltrack.measurement.activity;

/** 대상 1건의 활동 수준이 바뀌었을 때 발행되는 이벤트. */
public record ActivityLevelChangedEvent(Long memberId, Long subjectId, ActivityLevel level) {
}
