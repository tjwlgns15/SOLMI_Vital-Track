package com.solmi.vitaltrack.subject;

/**
 * 대상 종류. 화면에 보여줄 문구는 언어별로 달라야 하므로 여기에 문구를 직접 담지 않고,
 * 메시지 키만 들고 있는다 - 실제 문구 해석(Locale 적용)은 SubjectTypeLabels가 전담한다
 * (DuplicateLoginIdException이 문구 대신 데이터만 들고 있고, 실제 문구는 AuthController가
 * MessageSource로 구성하는 것과 같은 이유의 SRP).
 */
public enum SubjectType {

	HUMAN("subjectType.HUMAN"),
	ANIMAL("subjectType.ANIMAL");

	private final String messageKey;

	SubjectType(String messageKey) {
		this.messageKey = messageKey;
	}

	public String getMessageKey() {
		return messageKey;
	}
}
