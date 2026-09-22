package com.solmi.vitaltrack.measurement.realtime;

import com.solmi.vitaltrack.measurement.activity.ActivityLevel;

/** /topic/subjects/{id}/activity로 보내는, 대상의 현재 활동 수준 표현. */
public record ActivityStatus(String level, String label) {

	public static ActivityStatus from(ActivityLevel level) {
		return new ActivityStatus(level.name(), level.getLabel());
	}
}
