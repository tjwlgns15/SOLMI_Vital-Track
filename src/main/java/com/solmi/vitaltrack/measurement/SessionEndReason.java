package com.solmi.vitaltrack.measurement;

/**
 * 측정 세션이 어떻게 종료되었는지 구분한다.
 * 앱이 크래시 등으로 /end를 호출하지 못해도 서버가 방치된 세션을 스스로 정리할 수 있어야 하므로,
 * 사용자가 명시적으로 종료한 경우(NORMAL)와 서버가 타임아웃으로 대신 종료한 경우(TIMEOUT)를 구분해
 * 이력 화면 등에서 "비정상 종료" 여부를 보여줄 수 있게 한다.
 */
public enum SessionEndReason {

	NORMAL,
	TIMEOUT
}
