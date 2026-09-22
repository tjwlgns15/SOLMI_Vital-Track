package com.solmi.vitaltrack.measurement.activity;

/**
 * 가속도 신호로부터 판정하는 활동 수준. 보행 유형(속보/구보 등) 세부 분류는 하지 않고
 * 세 단계로만 단순화한다 - 그 이상은 주파수 분석 등이 필요해 이 프로젝트 스코프를 벗어난다.
 */
public enum ActivityLevel {

	REST("정지"),
	WALKING("보행"),
	ACTIVE("활발한 움직임");

	private final String label;

	ActivityLevel(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
