package com.solmi.vitaltrack.measurement.realtime;

import com.solmi.vitaltrack.measurement.activity.ActivityLevel;

/**
 * /topic/subjects/{id}/activity로 보내는, 대상의 현재 활동 수준 표현.
 * 이 레코드 자체는 문구를 어떻게 정할지 모른다 - 이미 Locale에 맞게 해석된 문구를
 * 호출자(ActivityAlertBroadcaster)가 넘겨준다.
 */
public record ActivityStatus(String level, String label) {

	public static ActivityStatus from(ActivityLevel level, String label) {
		return new ActivityStatus(level.name(), label);
	}
}
