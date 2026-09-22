package com.solmi.vitaltrack.subject;

public enum SubjectType {

	HUMAN("사람"),
	ANIMAL("동물");

	private final String label;

	SubjectType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
