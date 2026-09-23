package com.solmi.vitaltrack.measurement.activity;

/**
 * 가속도 신호로부터 판정하는 활동 수준. 보행 유형(속보/구보 등) 세부 분류는 하지 않고
 * 세 단계로만 단순화한다 - 그 이상은 주파수 분석 등이 필요해 이 프로젝트 스코프를 벗어난다.
 *
 * <p>화면에 보여줄 문구는 언어별로 달라야 하므로 여기에 문구를 직접 담지 않고 메시지 키만
 * 들고 있는다 - 실제 문구 해석은 ActivityLevelLabels가 전담한다(SubjectType/SubjectTypeLabels와
 * 같은 이유의 SRP).</p>
 */
public enum ActivityLevel {

	REST("activityLevel.REST"),
	WALKING("activityLevel.WALKING"),
	ACTIVE("activityLevel.ACTIVE");

	private final String messageKey;

	ActivityLevel(String messageKey) {
		this.messageKey = messageKey;
	}

	public String getMessageKey() {
		return messageKey;
	}
}
