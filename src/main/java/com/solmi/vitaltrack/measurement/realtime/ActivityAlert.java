package com.solmi.vitaltrack.measurement.realtime;

/**
 * /topic/subjects/{id}/alerts로 보내는 알림 메시지의 공통 형태. 서로 다른 활동 분석
 * 이벤트(RestDetectedEvent, AbnormalActivityDetectedEvent)를 같은 토픽으로 보내되,
 * type으로 구분할 수 있게 한다. 이 레코드 자체는 문구를 어떻게 구성할지 모른다 - 이미
 * Locale에 맞게 해석된 문구를 호출자(ActivityAlertBroadcaster)가 넘겨준다.
 */
public record ActivityAlert(String type, String message) {

	public static ActivityAlert rest(String message) {
		return new ActivityAlert("REST", message);
	}

	public static ActivityAlert abnormal(String message) {
		return new ActivityAlert("ABNORMAL", message);
	}
}
